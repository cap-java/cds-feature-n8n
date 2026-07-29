package sap.capire.n8n_plugin;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

public class N8nWebhookService {

  private static final Logger log = LoggerFactory.getLogger(N8nWebhookService.class);

  private final String baseUrl;
  private final String apiKey;
  private final RestClient restClient;

  public N8nWebhookService(String baseUrl, String apiKey, RestClient restClient) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiKey = apiKey != null ? apiKey : "";
    this.restClient = restClient;
  }

  // Only retry on network-level failures (ResourceAccessException = n8n unreachable).
  // HTTP errors (4xx/5xx) are not retried — n8n responded, so retrying won't help.
  // Both retry-exhausted and non-retried exceptions are caught by @Recover below,
  // which logs them and prevents propagation to the CAP event handler.
  @Retryable(
      retryFor = {ResourceAccessException.class},
      maxAttemptsExpression = "${n8n.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${n8n.retry.delay:2000}",
              multiplierExpression = "${n8n.retry.multiplier:2}"))
  public void notify(String path, Map<String, Object> payload) {
    log.info("Calling n8n webhook path={}", path);
    restClient
        .post()
        .uri(baseUrl + "/" + path)
        .header("X-Webhook-Secret", apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .toBodilessEntity();
  }
}
