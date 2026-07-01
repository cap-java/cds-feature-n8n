package sap.capire.n8n_plugin;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.outbox.OutboxMessage;
import com.sap.cds.services.outbox.OutboxService;
import java.util.Map;

@ServiceName(N8nService.DEFAULT_NAME)
public class N8nServiceHandler implements EventHandler {

  private final OutboxService outbox;

  public N8nServiceHandler(OutboxService outbox) {
    this.outbox = outbox;
  }

  // Handles the "trigger" event emitted by N8nServiceImpl, bridging the CAP event bus to the
  // outbox — the actual HTTP call happens in N8nOutboxHandler after the transaction commits.
  @On(event = "trigger")
  public void onTrigger(EventContext ctx) {
    String path = (String) ctx.get("path");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) ctx.get("data");

    OutboxMessage msg = OutboxMessage.create();
    msg.setParams(Map.of("path", path, "payload", payload));
    outbox.submit(N8nOutboxHandler.EVENT_TRIGGER, msg);

    ctx.setCompleted();
  }
}
