package sap.capire.n8n_plugin;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class N8nWebhookService {

    public static class WebhookConfig {
        private String url;
        private String apiKey = "";

        public String getUrl() { return url; }
        public void setUrl(String url) { this.url = url; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    private final Map<String, WebhookConfig> webhooks;

    public N8nWebhookService(Map<String, WebhookConfig> webhooks) {
        this.webhooks = webhooks;
    }

    private final RestClient restClient = RestClient.builder()
        .requestFactory(new SimpleClientHttpRequestFactory() {{
            setConnectTimeout(3000);
            setReadTimeout(5000);
        }})
        .build();

    @Retryable(
        retryFor = { RestClientException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void notify(String webhookName, Map<String, Object> payload) {
        WebhookConfig config = webhooks.get(webhookName);
        if (config == null || config.getUrl() == null) {
            System.err.println("No webhook configured for: " + webhookName);
            return;
        }
        restClient.post()
            .uri(config.getUrl())
            .header("X-Webhook-Secret", config.getApiKey())
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }

    @Recover
    public void recover(RestClientException e, String webhookName, Map<String, Object> payload) {
        System.err.println("All retries exhausted for '" + webhookName + "'. Error: " + e.getMessage());
    }
}
