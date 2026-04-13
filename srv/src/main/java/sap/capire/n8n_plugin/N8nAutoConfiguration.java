package sap.capire.n8n_plugin;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;

@Configuration
@EnableRetry
public class N8nAutoConfiguration {

    @Bean
    public N8nWebhookService n8nWebhookService() {
        return new N8nWebhookService();
    }
}
