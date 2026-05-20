package sap.capire.n8n_plugin;

import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.reflect.CdsAnnotatable;
import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsDeleteEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

// value="*" subscribes to every ApplicationService so any entity in any service can carry the annotation
// without the plugin needing to know service names at compile time
@ServiceName(value = "*", type = ApplicationService.class)
public class N8nHandler implements EventHandler {

    // Used in afterAction to skip CRUD events handled by onCrudEvent, preventing double-firing
    private static final Set<String> CRUD_EVENTS = Set.of(
        CqnService.EVENT_CREATE, CqnService.EVENT_READ,
        CqnService.EVENT_UPDATE, CqnService.EVENT_DELETE
    );

    private static final String ANNOTATION_START = "n8n.process.start";

    private final N8nWebhookService n8nWebhookService;

    public N8nHandler(N8nWebhookService n8nWebhookService) {
        this.n8nWebhookService = n8nWebhookService;
    }

    // Annotation-based path: entities annotated with @n8n.process.start trigger n8n webhooks
    // without requiring application code — the annotation alone is enough to wire up the integration.
    @After(event = { CqnService.EVENT_CREATE, CqnService.EVENT_READ, CqnService.EVENT_UPDATE, CqnService.EVENT_DELETE })
    public void onCrudEvent(EventContext ctx) {
        CdsStructuredType entity = ctx.getTarget();
        if (entity == null) return;
        String event = ctx.getEvent();
        findTrigger(entity, event).ifPresent(trigger -> {
            // "on" is the event name in the annotation (e.g. "CREATE") and also the webhook name to call in n8n
            String on = (String) trigger.get("on");

            Map<String, Object> row;
            if (ctx instanceof CdsCreateEventContext createCtx) {
                if (createCtx.getCqn().entries().isEmpty()) return;
                // CQN entries hold all rows in the INSERT; we take the first because the @After handler
                // fires once per statement, and a standard single-entity POST has exactly one entry
                row = createCtx.getCqn().entries().get(0);
            } else if (ctx instanceof CdsUpdateEventContext updateCtx) {
                if (updateCtx.getCqn().entries().isEmpty()) return;
                // only the changed fields are in the CQN entries — unchanged fields are absent from the payload
                row = updateCtx.getCqn().entries().get(0);
            } else if (ctx instanceof CdsReadEventContext readCtx) {
                // CAP populates the result before @After handlers run, so it is safe to read here
                var first = readCtx.getResult().stream().findFirst();
                if (first.isEmpty()) return;
                row = first.get();
            } else if (ctx instanceof CdsDeleteEventContext deleteCtx) {
                row = extractDeleteKeys(deleteCtx);
            } else {
                return;
            }

            @SuppressWarnings("unchecked")
            List<Object> inputs = (List<Object>) trigger.get("inputs");
            Map<String, Object> payload = inputs != null && !inputs.isEmpty() ? InputExtractor.extract(inputs, row) : row;
            n8nWebhookService.notify(on, payload);
        });
    }

    protected Map<String, Object> extractDeleteKeys(CdsDeleteEventContext deleteCtx) {
        return CqnAnalyzer.create(deleteCtx.getModel()).analyze(deleteCtx.getCqn()).rootKeys();
    }

    // The annotation value is a list of trigger configs (e.g. [{on: 'CREATE'}, {on: 'DELETE'}]).
    // Returns the matching trigger config, used to read "on" (webhook name) and "inputs" (field selection),
    // or empty if the annotation has no entry for this event.
    private java.util.Optional<Map<String, Object>> findTrigger(CdsAnnotatable annotatable, String event) {
        List<Map<String, Object>> triggers = annotatable.getAnnotationValue(ANNOTATION_START, List.of());
        return triggers.stream()
            .filter(t -> event.equals(t.get("on")))
            .findFirst(); // if the same event appears twice in the annotation, fire only once
    }

    // event="*" catches custom actions and functions whose names are unknown at compile time;
    // CRUD events are filtered out immediately because they are handled by onCrudEvent above
    @After(event = "*")
    public void afterAction(EventContext ctx) {
        if (CRUD_EVENTS.contains(ctx.getEvent())) return;
        CdsAnnotatable annotatable = ctx.getTarget();

        // Unbound actions have no target entity, so we look the action up by name in the service model
        if (annotatable == null) {
            String serviceName = ctx.getService().getName();
            String eventName = ctx.getEvent();
            annotatable = ctx.getModel()
                .findService(serviceName)
                .flatMap(svc -> svc.actions()
                    .filter(a -> a.getName().equals(eventName))
                    // findFirst() because an action name is unique within a service
                    .findFirst())
                .orElse(null);
        }
        if (annotatable == null) return;

        String on = annotatable.getAnnotationValue(ANNOTATION_START + ".on", (String) null);
        if (!ctx.getEvent().equals(on)) return;

        String path = annotatable.getAnnotationValue(ANNOTATION_START + ".path", (String) null);
        if (path == null) return;

        Map<String, Object> data = new HashMap<>();
        ctx.keySet().forEach(k -> data.put(k, ctx.get(k)));

        List<Object> inputs = annotatable.getAnnotationValue(ANNOTATION_START + ".inputs", List.of());
        n8nWebhookService.notify(on, inputs.isEmpty() ? data : InputExtractor.extract(inputs, data));
    }
}
