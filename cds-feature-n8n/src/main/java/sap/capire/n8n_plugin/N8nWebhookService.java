package sap.capire.n8n_plugin;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
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

  // Only retry on network-level failures (connection refused, timeout) — ResourceAccessException
  // wraps IOExceptions and means n8n was not reachable at all.
  // HTTP error responses (4xx, 5xx) mean n8n responded, so retrying won't help:
  // 4xx = misconfiguration, 5xx = the workflow itself failed.
  @Retryable(
      retryFor = {ResourceAccessException.class},
      maxAttemptsExpression = "${n8n.retry.max-attempts:3}",
      backoff =
          @Backoff(
              delayExpression = "${n8n.retry.delay:2000}",
              multiplierExpression = "${n8n.retry.multiplier:2}"))
  public void notify(String path, Map<String, Object> payload) {
    restClient
        .post()
        .uri(baseUrl + "/" + path)
        .header("X-Webhook-Secret", apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .toBodilessEntity();
  }

  @Recover
  public void recover(Exception e, String path, Map<String, Object> payload) {
    if (e instanceof ResourceAccessException) {
      log.error("n8n unreachable after all retries for '{}': {}", path, e.getMessage());
    } else if (e instanceof HttpStatusCodeException httpEx) {
      log.error("n8n returned {} for '{}' — check webhook path and workflow configuration",
          httpEx.getStatusCode(), path);
    } else {
      log.error("Unexpected error calling webhook '{}': {}", path, e.getMessage());
    }
  }
}
