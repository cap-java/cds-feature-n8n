package sap.capire.n8n_plugin;

import java.util.Map;
import org.springframework.http.MediaType;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Recover;
import org.springframework.retry.annotation.Retryable;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

public class N8nWebhookService {

    private final String baseUrl;
    private final String apiKey;
    private final RestClient restClient;

    public N8nWebhookService(String baseUrl, String apiKey, RestClient restClient) {
        this.baseUrl = baseUrl;
        this.apiKey = apiKey != null ? apiKey : "";
        this.restClient = restClient;
    }

    @Retryable(
        retryFor = { RestClientException.class },
        maxAttempts = 3,
        backoff = @Backoff(delay = 2000, multiplier = 2)
    )
    public void notify(String path, Map<String, Object> payload) {
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
        System.err.println("All retries exhausted for '" + webhookName + "'. Error: " + e.getMessage());
    }
}
