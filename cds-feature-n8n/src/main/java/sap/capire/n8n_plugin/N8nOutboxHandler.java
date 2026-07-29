package sap.capire.n8n_plugin;

import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.outbox.OutboxMessageEventContext;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;

@ServiceName(N8nOutboxHandler.OUTBOX_NAME)
public class N8nOutboxHandler implements EventHandler {

  static final String OUTBOX_NAME = "N8nOutbox";
  static final String EVENT_TRIGGER = "n8n.trigger";

  private static final Logger log = LoggerFactory.getLogger(N8nOutboxHandler.class);

  private final N8nWebhookService n8nWebhookService;

  public N8nOutboxHandler(N8nWebhookService n8nWebhookService) {
    this.n8nWebhookService = n8nWebhookService;
  }

  @On(event = EVENT_TRIGGER)
  public void onTrigger(OutboxMessageEventContext ctx) {
    Map<String, Object> params = ctx.getMessage().getParams();
    String path = (String) params.get("path");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) params.get("payload");

    try {
      n8nWebhookService.notify(path, payload);
      ctx.setCompleted();
    } catch (ResourceAccessException e) {
      // n8n unreachable — throw so the outbox retries with backoff
      log.warn("n8n unreachable for path='{}', outbox will retry: {}", path, e.getMessage());
      throw e;
    } catch (HttpStatusCodeException e) {
      // HTTP error response means n8n received the request but the workflow failed.
      // Retrying won't help — mark completed to avoid poisoning the queue.
      log.error("n8n returned {} for path='{}' — check webhook and workflow config",
          e.getStatusCode(), path);
      ctx.setCompleted();
    }
  }
}
