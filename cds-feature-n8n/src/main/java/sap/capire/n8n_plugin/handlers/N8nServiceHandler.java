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
import org.springframework.http.HttpMethod;
import sap.capire.n8n_plugin.configuration.N8nAutoConfiguration.N8nProperties;
import sap.capire.n8n_plugin.services.N8nService;
import sap.capire.n8n_plugin.services.N8nWebhookService;

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
  private final N8nProperties props;
  private final N8nWebhookService webhookService;

  /**
   * @param outbox the persistent outbox service qualified as {@code N8nOutbox}
   * @param props plugin configuration; used to detect console mode
   * @param webhookService used for direct (non-outboxed) delivery in console mode
   */
  public N8nServiceHandler(
      OutboxService outbox, N8nProperties props, N8nWebhookService webhookService) {
    if (outbox == null && !props.isUseConsole()) {
      throw new IllegalStateException(
          "N8nServiceHandler requires an OutboxService when n8n.use-console=false");
    }
    this.outbox = outbox;
    this.props = props;
    this.webhookService = webhookService;
  }

  /**
   * Handles the {@code trigger} event. In console mode delivers synchronously via {@link
   * N8nWebhookService#notify}; otherwise submits an outbox message for deferred HTTP delivery.
   *
   * @param ctx event context carrying {@code path} and {@code data} set by {@link
   *     sap.capire.n8n_plugin.services.N8nServiceImpl#trigger}
   */
  @On(event = "trigger")
  public void onTrigger(EventContext ctx) {
    String path = (String) ctx.get("path");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) ctx.get("data");
    HttpMethod method =
        ctx.get("method") instanceof String m ? HttpMethod.valueOf(m) : HttpMethod.POST;
    if (props.isUseConsole()) {
      webhookService.notify(path, payload, method);
      ctx.setCompleted();
      return;
    }

    OutboxMessage msg = OutboxMessage.create();
    msg.setParams(Map.of("path", path, "payload", payload, "method", method.name()));
    outbox.submit(N8nOutboxHandler.EVENT_TRIGGER, msg);
    ctx.setCompleted();
  }
}
