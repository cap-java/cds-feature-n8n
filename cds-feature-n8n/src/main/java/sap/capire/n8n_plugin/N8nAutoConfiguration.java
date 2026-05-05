package sap.capire.n8n_plugin;

import java.util.HashMap;
import java.util.Map;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestClient;
import sap.capire.n8n_plugin.N8nWebhookService.WebhookConfig;

@Configuration
@EnableRetry
@EnableConfigurationProperties(N8nAutoConfiguration.N8nProperties.class)
public class N8nAutoConfiguration {

    @ConfigurationProperties(prefix = "n8n")
    public static class N8nProperties {
        private Map<String, WebhookConfig> webhooks = new HashMap<>();

        public Map<String, WebhookConfig> getWebhooks() { return webhooks; }
        public void setWebhooks(Map<String, WebhookConfig> webhooks) { this.webhooks = webhooks; }
    }

    @Bean
    public RestClient n8nRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean
    public N8nWebhookService n8nWebhookService(N8nProperties props, RestClient n8nRestClient) {
        return new N8nWebhookService(props.getWebhooks(), n8nRestClient);
    }

    @Bean
    public N8nHandler n8nHandler(N8nWebhookService n8nWebhookService) {
        return new N8nHandler(n8nWebhookService);
    }
}
