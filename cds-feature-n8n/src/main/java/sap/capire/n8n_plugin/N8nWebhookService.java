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

  // Throws ResourceAccessException on network failure (n8n unreachable) — the outbox catches this
  // and retries with exponential backoff. HTTP error responses (4xx/5xx) are thrown as
  // HttpStatusCodeException and handled by N8nOutboxHandler without retrying.
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
