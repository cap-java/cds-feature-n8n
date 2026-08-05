/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.handlers;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.outbox.OutboxMessage;
import com.sap.cds.services.outbox.OutboxService;
import java.util.Map;
import sap.capire.n8n_plugin.services.N8nService;

/**
 * CAP event handler that bridges {@link sap.capire.n8n_plugin.services.N8nService#trigger} calls to
 * the persistent outbox.
 *
 * <p>Listens on the {@code N8nService} CAP service for the {@code trigger} event emitted by {@link
 * sap.capire.n8n_plugin.services.N8nServiceImpl}. The actual HTTP call to n8n happens in {@link
 * N8nOutboxHandler} after the transaction commits.
 */
@ServiceName(N8nService.DEFAULT_NAME)
public class N8nServiceHandler implements EventHandler {

  private final OutboxService outbox;

  /**
   * @param outbox the persistent outbox service qualified as {@code N8nOutbox}
   */
  public N8nServiceHandler(OutboxService outbox) {
    this.outbox = outbox;
  }

  /**
   * Handles the {@code trigger} event by submitting an outbox message for deferred HTTP delivery.
   *
   * @param ctx event context carrying {@code path} and {@code data} set by {@link
   *     sap.capire.n8n_plugin.services.N8nServiceImpl#trigger}
   */
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
