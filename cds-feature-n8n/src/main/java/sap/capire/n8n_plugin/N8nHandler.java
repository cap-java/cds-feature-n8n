package sap.capire.n8n_plugin;

import com.sap.cds.reflect.CdsAnnotatable;
import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.ApplicationService;
mvn clean import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

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
    public void afterCreate(CdsCreateEventContext ctx) {
        CdsStructuredType entity = ctx.getTarget();
        if (entity == null) return;
        findTrigger(entity, "CREATE").ifPresent(name -> {
        Map<String, Object> payload = new HashMap<>();
        payload.put("event", "CREATE");
        payload.put("entity", entity.getQualifiedName());
        payload.put("user", ctx.getUserInfo().getName());

        // add the inserted book data
        if (!ctx.getCqn().entries().isEmpty()) {
            Map<String, Object> book = ctx.getCqn().entries().get(0);
            payload.put("id",     book.get("ID"));
            payload.put("title",  book.get("title"));
            payload.put("author", book.get("author_ID"));
            payload.put("stock",  book.get("stock"));
        }

        n8nWebhookService.notify(name, payload);
        });
    }

    @On(event = CqnService.EVENT_DELETE)
    public void afterDelete(EventContext ctx) {
        CdsStructuredType entity = ctx.getTarget();
        if (entity == null) return;
        findTrigger(entity, "DELETE").ifPresent(name ->
            n8nWebhookService.notify(name, Map.of(
                "event", "DELETE",
                "entity", entity.getQualifiedName()
            ))
        );
    }
    
    private java.util.Optional<String> findTrigger(CdsAnnotatable annotatable, String event) {
        List<Map<String, Object>> triggers = annotatable.getAnnotationValue(ANNOTATION_START, List.of());
        return triggers.stream()
            .filter(t -> event.equals(t.get("on")))
            .map(t -> (String) t.get("on"))
            .findFirst();
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
        if (CRUD_EVENTS.contains(ctx.getEvent())) return;
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

        // Check if the annotation exists and if the event matches
        String on = annotatable.getAnnotationValue(ANNOTATION_START + ".on", (String) null);
        if (!ctx.getEvent().equals(on)) return;

        Map<String, Object> payload = new HashMap<>();
        ctx.keySet().forEach(k -> payload.put(k, ctx.get(k)));
        payload.put("event", on);
        payload.remove("result");
        n8nWebhookService.notify(on, payload);
    }
}
