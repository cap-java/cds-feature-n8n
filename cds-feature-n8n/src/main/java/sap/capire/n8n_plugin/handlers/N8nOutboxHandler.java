/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.handlers;

import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.On;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.outbox.OutboxMessageEventContext;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import sap.capire.n8n_plugin.services.N8nWebhookService;

/**
 * CAP outbox handler that delivers queued n8n webhook calls over HTTP.
 *
 * <p>Listens on the {@code N8nOutbox} outbox service for {@code n8n.trigger} messages submitted by
 * {@link N8nHandler} and {@link N8nServiceHandler}. Runs after the business transaction commits, so
 * a failing HTTP call never rolls back application data.
 *
 * <p>Retry semantics:
 *
 * <ul>
 *   <li>Network errors ({@link org.springframework.web.client.ResourceAccessException}) — rethrown
 *       so the outbox retries with backoff.
 *   <li>HTTP 4xx/5xx ({@link org.springframework.web.client.HttpStatusCodeException}) — logged and
 *       marked completed; retrying a misconfigured or workflow-failed call would not help.
 * </ul>
 */
@ServiceName(N8nOutboxHandler.OUTBOX_NAME)
public class N8nOutboxHandler implements EventHandler {

  /** CAP outbox service name; used as the {@code @Qualifier} when injecting the outbox bean. */
  public static final String OUTBOX_NAME = "N8nOutbox";

  /** Event name used to identify n8n trigger messages in the outbox. */
  public static final String EVENT_TRIGGER = "n8n.trigger";

  private static final Logger log = LoggerFactory.getLogger(N8nOutboxHandler.class);

  private final N8nWebhookService n8nWebhookService;

  /**
   * @param n8nWebhookService the HTTP layer used to POST to n8n
   */
  public N8nOutboxHandler(N8nWebhookService n8nWebhookService) {
    this.n8nWebhookService = n8nWebhookService;
  }

  /**
   * Processes a single outbox message by calling the n8n webhook.
   *
   * @param ctx outbox message context carrying {@code path} {@code payload}, and {@code method}
   *     params
   */
  @On(event = EVENT_TRIGGER)
  public void onTrigger(OutboxMessageEventContext ctx) {
    Map<String, Object> params = ctx.getMessage().getParams();
    String path = (String) params.get("path");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) params.get("payload");
    HttpMethod method =
        params.get("method") instanceof String m ? HttpMethod.valueOf(m) : HttpMethod.POST;
    try {
      n8nWebhookService.notify(path, payload, method);
      ctx.setCompleted();
    } catch (ResourceAccessException e) {
      // n8n unreachable — throw so the outbox retries with backoff
      log.warn("n8n unreachable for path='{}', outbox will retry: {}", path, e.getMessage());
      throw e;
    } catch (HttpStatusCodeException e) {
      // HTTP error response means n8n received the request but the workflow failed.
      // Retrying won't help — mark completed to avoid poisoning the queue.
      log.error(
          "n8n returned {} for path='{}' — check webhook and workflow config",
          e.getStatusCode(),
          path);
      ctx.setCompleted();
    }
  }
}
