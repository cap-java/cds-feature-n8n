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

    private final String n8nWebhookUrl;
    private final String apiKey;

    public N8nWebhookService() {
        this.n8nWebhookUrl = null;
        this.apiKey = null;
    }

    public N8nWebhookService(String n8nWebhookUrl, String apiKey) {
        this.n8nWebhookUrl = n8nWebhookUrl;
        this.apiKey = apiKey;
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
    public void notify(Map<String, Object> payload) {
        restClient.post()
            .uri(n8nWebhookUrl)
            .header("X-Webhook-Secret", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }

    @Recover
    public void recover(RestClientException e, Map<String, Object> payload) {
        System.err.println("All retries exhausted. Could not notify n8n. Error: " + e.getMessage());
    }
}
