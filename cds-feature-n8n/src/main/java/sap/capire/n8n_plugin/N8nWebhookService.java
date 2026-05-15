package sap.capire.n8n_plugin;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class N8nWebhookService {

    private static final Logger log = LoggerFactory.getLogger(N8nWebhookService.class);

    public static class WebhookConfig {
        private String url;
        private String apiKey = "";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    private final Map<String, WebhookConfig> webhooks;
    private final RestClient restClient;

    public N8nWebhookService(String baseUrl, String apiKey, RestClient restClient) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey != null ? apiKey : "";
        this.restClient = restClient;
    }

    // 4xx errors (unauthorized, bad request) signal misconfiguration, not transient failures — retrying them would be pointless.
    // SpEL expressions with defaults let consuming apps tune retry behaviour in application.yaml without changing the plugin.
    @Retryable(
        retryFor = { RestClientException.class },
        noRetryFor = { HttpClientErrorException.class },
        maxAttemptsExpression = "${n8n.retry.max-attempts:3}",
        backoff = @Backoff(
            delayExpression = "${n8n.retry.delay:2000}",
            multiplierExpression = "${n8n.retry.multiplier:2}"
        )
    )
    public void notify(String webhookName, Map<String, Object> payload) {
        WebhookConfig config = webhooks.get(webhookName);
        if (config == null || config.getUrl() == null) {
            log.warn("No webhook configured for: {}", webhookName);
            return;
        }
        restClient.post()
            .uri(baseUrl + "/" + path)
            .header("X-Webhook-Secret", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }

    @Recover
    public void recover(RestClientException e, String webhookName, Map<String, Object> payload) {
        log.error("All retries exhausted for '{}'. Error: {}", webhookName, e.getMessage());
    }
}
