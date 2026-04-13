package sap.capire.n8n_plugin;

import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.ServiceName;

import java.util.Map;

@ServiceName(value = "*", type = ApplicationService.class)
public class N8nHandler implements EventHandler {

    private static final String ANNOTATION = "n8n.process.start.on";

    private final N8nWebhookService n8nWebhookService;

    public N8nHandler(N8nWebhookService n8nWebhookService) {
        this.n8nWebhookService = n8nWebhookService;
    }

    @After(event = CqnService.EVENT_CREATE)
    public void afterCreate(EventContext ctx) {
        CdsStructuredType entity = ctx.getTarget();
        String on = entity.getAnnotationValue(ANNOTATION, (String) null);
        if ("CREATE".equals(on)) {
            n8nWebhookService.notify(Map.of(
                "event", "CREATE",
                "entity", entity.getQualifiedName()
            ));
        }
    }

    @After(event = CqnService.EVENT_DELETE)
    public void afterDelete(EventContext ctx) {
        CdsStructuredType entity = ctx.getTarget();
        String on = entity.getAnnotationValue(ANNOTATION, (String) null);
        if ("DELETE".equals(on)) {
            n8nWebhookService.notify(Map.of(
                "event", "DELETE",
                "entity", entity.getQualifiedName()
            ));
        }
    }
}
