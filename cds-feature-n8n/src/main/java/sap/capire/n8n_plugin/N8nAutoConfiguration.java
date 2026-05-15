package sap.capire.n8n_plugin;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestClient;

// @EnableRetry activates Spring Retry's AOP proxy; without it @Retryable annotations are silently ignored
@Configuration
@EnableRetry
@EnableConfigurationProperties(N8nAutoConfiguration.N8nProperties.class)
public class N8nAutoConfiguration {

    // Inner class keeps plugin configuration scoped here rather than polluting the consuming app's config namespace
    @ConfigurationProperties(prefix = "n8n")
    public static class N8nProperties {
        private String baseUrl;
        private String apiKey = "";

        public String getBaseUrl() { return baseUrl; }
        public void setBaseUrl(String baseUrl) { this.baseUrl = baseUrl; }
        public String getApiKey() { return apiKey; }
        public void setApiKey(String apiKey) { this.apiKey = apiKey; }
    }

    @Bean
    public RestClient n8nRestClient() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        // Explicit timeouts prevent a slow or unreachable n8n instance from blocking the CAP request thread
        factory.setConnectTimeout(3000);
        factory.setReadTimeout(5000);
        return RestClient.builder().requestFactory(factory).build();
    }

    @Bean
    public N8nWebhookService n8nWebhookService(N8nProperties props, RestClient n8nRestClient) {
        return new N8nWebhookService(props.baseUrl, props.apiKey, n8nRestClient);
    }

    @Bean
    public N8nHandler n8nHandler(N8nWebhookService n8nWebhookService) {
        return new N8nHandler(n8nWebhookService);
    }

    // Return type is the interface so callers depend on the abstraction, not the concrete class
    @Bean
    public N8nService n8nService() {
        return new N8nServiceImpl(N8nService.DEFAULT_NAME);
    }

    @Bean
    public N8nServiceHandler n8nServiceHandler(N8nWebhookService n8nWebhookService) {
        return new N8nServiceHandler(n8nWebhookService);
    }
}
