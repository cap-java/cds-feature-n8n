package sap.capire.n8n_plugin;

import com.sap.cds.reflect.CdsAction;
import com.sap.cds.reflect.CdsAnnotatable;
import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@ServiceName(value = "*", type = ApplicationService.class)
public class N8nHandler implements EventHandler {

    private static final String ANNOTATION_ON = "n8n.process.start.on";
    private static final String ANNOTATION_NAME = "n8n.process.start.name";
    private static final String ANNOTATION_DELETE_ON = "n8n.process.delete.on";
    private static final String ANNOTATION_DELETE_NAME = "n8n.process.delete.name";

    private final N8nWebhookService n8nWebhookService;

    public N8nHandler(N8nWebhookService n8nWebhookService) {
        this.n8nWebhookService = n8nWebhookService;
    }

    @On(event = {CqnService.EVENT_CREATE})
    public void afterCreate(EventContext ctx) {
        CdsStructuredType entity = ctx.getTarget();
        if (entity == null) return;
        String on = entity.getAnnotationValue(ANNOTATION_ON, (String) null);
        System.out.println("[N8nHandler] afterCreate fired: event=" + ctx.getEvent() + ", entity=" + entity.getQualifiedName() + ", annotation.on=" + on);
        if ("CREATE".equals(on)) {
            String name = entity.getAnnotationValue(ANNOTATION_NAME, "CREATE");
            n8nWebhookService.notify(name, Map.of(
                "event", "CREATE",
                "entity", entity.getQualifiedName()
            ));
        }
    }

    @On(event = CqnService.EVENT_DELETE)
    public void afterDelete(EventContext ctx) {
        CdsStructuredType entity = ctx.getTarget();
        System.out.println("[N8nHandler] afterDelete fired: entity=" + (entity != null ? entity.getQualifiedName() : "null"));
        if (entity == null) return;
        String on = entity.getAnnotationValue(ANNOTATION_DELETE_ON, (String) null);
        System.out.println("[N8nHandler] afterDelete annotation.on=" + on);
        if ("DELETE".equals(on)) {
            String name = entity.getAnnotationValue(ANNOTATION_DELETE_NAME, "DELETE");
            n8nWebhookService.notify(name, Map.of(
                "event", "DELETE",
                "entity", entity.getQualifiedName()
            ));
        }
    }

    @On(event = "confirmOrder")
    public void onConfirmOrder(EventContext ctx) {
        Map<String, Object> result = new HashMap<>();
        result.put("orderId", "ORD-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        ctx.put("result", result);
        ctx.setCompleted();
    }

    @After(event = "*")
    public void afterAction(EventContext ctx) {
        CdsAnnotatable annotatable = ctx.getTarget();
        if (annotatable == null) {
            // unbound action — look it up on the service
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
        String on = annotatable.getAnnotationValue(ANNOTATION_ON, (String) null);
        if (!ctx.getEvent().equals(on)) return;

        String name = annotatable.getAnnotationValue(ANNOTATION_NAME, ctx.getEvent());
        Map<String, Object> payload = new HashMap<>();
        ctx.keySet().forEach(k -> payload.put(k, ctx.get(k)));
        payload.put("event", name);
        payload.remove("result");
        n8nWebhookService.notify(name, payload);
    }
}
