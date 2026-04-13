package sap.capire.n8n_plugin;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class N8nAutoConfiguration {

    @Bean
    @ConditionalOnProperty(name = "n8n.webhook.url")
    public N8nWebhookService n8nWebhookService(
            @Value("${n8n.webhook.url}") String webhookUrl,
            @Value("${n8n.webhook.apiKey:}") String apiKey) {
        return new N8nWebhookService(webhookUrl, apiKey);
    }

    @Bean
    @ConditionalOnProperty(name = "n8n.webhook.url")
    public N8nHandler n8nHandler(N8nWebhookService n8nWebhookService) {
        return new N8nHandler(n8nWebhookService);
    }
}
