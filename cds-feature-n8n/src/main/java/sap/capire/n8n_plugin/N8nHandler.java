package sap.capire.n8n_plugin;

import com.sap.cds.reflect.CdsAnnotatable;
import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsCreateEventContext;
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

    // Used in afterAction to skip CRUD events that already have dedicated handlers, preventing double-firing
    private static final Set<String> CRUD_EVENTS = Set.of(
        CqnService.EVENT_CREATE, CqnService.EVENT_READ,
        CqnService.EVENT_UPDATE, CqnService.EVENT_DELETE
    );

    private static final String ANNOTATION_START = "n8n.process.start";

    private final N8nWebhookService n8nWebhookService;

    public N8nHandler(N8nWebhookService n8nWebhookService) {
        this.n8nWebhookService = n8nWebhookService;
    }

    // @After ensures n8n is only notified once the CAP operation has successfully completed
    @After(event = CqnService.EVENT_CREATE)
    public void afterCreate(CdsCreateEventContext ctx) {
        CdsStructuredType entity = ctx.getTarget();
        if (entity == null) return;
        findTrigger(entity, "CREATE").ifPresent(path -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "CREATE");
            payload.put("entity", entity.getQualifiedName());
            payload.put("user", ctx.getUserInfo().getName());

            if (!ctx.getCqn().entries().isEmpty()) {
                payload.put("data", ctx.getCqn().entries().get(0));
            }

            n8nWebhookService.notify(path, payload);
        });
    }

    @After(event = CqnService.EVENT_DELETE)
    public void afterDelete(EventContext ctx) {
        CdsStructuredType entity = ctx.getTarget();
        if (entity == null) return;
        findTrigger(entity, "DELETE").ifPresent(path -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "DELETE");
            payload.put("entity", entity.getQualifiedName());
            payload.put("user", ctx.getUserInfo().getName());
            n8nWebhookService.notify(path, payload);
        });
    }

    // The annotation value is a list of trigger configs (e.g. [{on: 'CREATE'}, {on: 'DELETE'}]),
    // so we search for the entry matching the current event rather than assuming a single value.
    // findFirst() is used because having two entries for the same event would be a config mistake —
    // we only ever want to fire once per event.
    // Returns the matched "on" value (e.g. "CREATE"), which doubles as the webhook name to look up in config,
    // or empty if the entity has no matching trigger annotation for this event.
    private java.util.Optional<String> findTrigger(CdsAnnotatable annotatable, String event) {
        List<Map<String, Object>> triggers = annotatable.getAnnotationValue(ANNOTATION_START, List.of());
        return triggers.stream()
            .filter(t -> event.equals(t.get("on")))
            .map(t -> (String) t.get("path"))
            .findFirst();
    }

    // event="*" catches custom actions and functions whose names are unknown at compile time;
    // CRUD events are filtered out immediately because they are handled by the dedicated methods above
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

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", on);
        payload.put("entity", ctx.getTarget() != null ? ctx.getTarget().getQualifiedName() : ctx.getEvent());
        payload.put("user", ctx.getUserInfo().getName());
        payload.put("data", data);
        n8nWebhookService.notify(path, payload);
    }
}
