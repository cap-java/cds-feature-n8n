package sap.capire.n8n_plugin;

import java.util.Map;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;

@ServiceName(N8nService.DEFAULT_NAME)
public class N8nServiceHandler implements EventHandler {
    private N8nWebhookService n8nWebhookService;

    public N8nServiceHandler(N8nWebhookService n8nWebhookService) {
        this.n8nWebhookService = n8nWebhookService;
    }

    @On(event = "trigger")
    public void onTrigger(EventContext ctx) {
        String webhookName = (String) ctx.get("webhookName");
        @SuppressWarnings("unchecked")
        Map<String, Object> data = (Map<String, Object>) ctx.get("data");
        n8nWebhookService.notify(webhookName, data);
        ctx.setCompleted();
    }

    
}
