package customer.bookshop.handlers;

import java.util.HashMap;
import java.util.Map;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import cds.gen.catalogservice.SubmitOrderContext;
import org.springframework.http.client.SimpleClientHttpRequestFactory;



@Service
public class N8nWebhookService {
    @Value ("${n8n.webhook.url}")
    private String n8nWebhookUrl;

    @Value("${n8n.webhook.apiKey}")
    private String apiKey;


    private final RestClient restClient = RestClient.builder()
    .requestFactory(new SimpleClientHttpRequestFactory() {{
        setConnectTimeout(3000);  // 3 seconds to connect
        setReadTimeout(5000);     // 5 seconds to read response
    }})
    .build();



    public void notifyOrderCreated(SubmitOrderContext context) {
        Map<String, Object> payload = new HashMap<>();
        payload.put("book", context.getBook());
        payload.put("quantity", context.getQuantity());
        payload.put("buyer", context.getUserInfo().getName());

        
        restClient.post()
            .uri(n8nWebhookUrl)
            .header("X-Webhook-Secret", apiKey)
            .contentType(MediaType.APPLICATION_JSON)
            .body(payload)
            .retrieve()
            .toBodilessEntity();
    }
    
}
