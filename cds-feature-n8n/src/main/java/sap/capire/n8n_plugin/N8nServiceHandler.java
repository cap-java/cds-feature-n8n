package sap.capire.n8n_plugin;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import java.util.Map;

@ServiceName(N8nService.DEFAULT_NAME)
public class N8nServiceHandler implements EventHandler {
  private N8nWebhookService n8nWebhookService;

  public N8nServiceHandler(N8nWebhookService n8nWebhookService) {
    this.n8nWebhookService = n8nWebhookService;
  }

  // Handles the "trigger" event emitted by N8nServiceImpl, bridging the CAP event bus to the HTTP
  // call by calling the notify() method
  @On(event = "trigger")
  public void onTrigger(EventContext ctx) {
    String path = (String) ctx.get("path");
    @SuppressWarnings("unchecked")
    Map<String, Object> data = (Map<String, Object>) ctx.get("data");
    n8nWebhookService.notify(path, data);
    // setCompleted() tells CAP the event is fully handled and stops it propagating further down the
    // handler chain
    ctx.setCompleted();
  }
}
