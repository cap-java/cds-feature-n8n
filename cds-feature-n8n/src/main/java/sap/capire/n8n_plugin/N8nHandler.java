package sap.capire.n8n_plugin;

import com.sap.cds.reflect.CdsAnnotatable;
import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@ServiceName(value = "*", type = ApplicationService.class)
public class N8nHandler implements EventHandler {


    private static final Set<String> CRUD_EVENTS = Set.of(
        CqnService.EVENT_CREATE, CqnService.EVENT_READ,
        CqnService.EVENT_UPDATE, CqnService.EVENT_DELETE
    );

    private static final String ANNOTATION_START = "n8n.process.start";

    private final N8nWebhookService n8nWebhookService;

    public N8nHandler(N8nWebhookService n8nWebhookService) {
        this.n8nWebhookService = n8nWebhookService;
    }

    @On(event = {CqnService.EVENT_CREATE})
    public void onCreate(CdsCreateEventContext ctx) {
        CdsStructuredType entity = ctx.getTarget();
        if (entity == null) return;
        findTrigger(entity, "CREATE").ifPresent(name -> {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "CREATE");
        payload.put("entity", entity.getQualifiedName());
        payload.put("user", ctx.getUserInfo().getName());

        if (!ctx.getCqn().entries().isEmpty()) {
            payload.put("data", ctx.getCqn().entries().get(0));
        }

        n8nWebhookService.notify(name, payload);
        });
    }

    @On(event = CqnService.EVENT_DELETE)
    public void onDelete(EventContext ctx) {
        CdsStructuredType entity = ctx.getTarget();
        if (entity == null) return;
        findTrigger(entity, "DELETE").ifPresent(on -> {
            Map<String, Object> payload = new HashMap<>();
            payload.put("event", "DELETE");
            payload.put("entity", entity.getQualifiedName());
            payload.put("user", ctx.getUserInfo().getName());
            n8nWebhookService.notify(on, payload);
        });
    }
    
    private java.util.Optional<String> findTrigger(CdsAnnotatable annotatable, String event) {
        List<Map<String, Object>> triggers = annotatable.getAnnotationValue(ANNOTATION_START, List.of());
        return triggers.stream()
            .filter(t -> event.equals(t.get("on")))
            .map(t -> (String) t.get("on"))
            .findFirst();
    }

    @After(event = "*")
    public void afterAction(EventContext ctx) {
        if (CRUD_EVENTS.contains(ctx.getEvent())) return;
        CdsAnnotatable annotatable = ctx.getTarget();

        // If there's no target, it might be an unbound action/function — try to find it on the service
        if (annotatable == null) {
            String serviceName = ctx.getService().getName();
            String eventName = ctx.getEvent();
            annotatable = ctx.getModel()
                .findService(serviceName)
                .flatMap(svc -> svc.actions()
                    .filter(a -> a.getName().equals(eventName))
                    .findFirst())
                .orElse(null);
        }
        if (annotatable == null) return;

        // Check if the annotation exists and if the event matches
        String on = annotatable.getAnnotationValue(ANNOTATION_START + ".on", (String) null);
        if (!ctx.getEvent().equals(on)) return;

        Map<String, Object> data = new HashMap<>();
        ctx.keySet().forEach(k -> data.put(k, ctx.get(k)));

        Map<String, Object> payload = new HashMap<>();
        payload.put("event", on);
        payload.put("entity", ctx.getTarget() != null ? ctx.getTarget().getQualifiedName() : ctx.getEvent());
        payload.put("user", ctx.getUserInfo().getName());
        payload.put("data", data);
        n8nWebhookService.notify(on, payload);
    }
}
