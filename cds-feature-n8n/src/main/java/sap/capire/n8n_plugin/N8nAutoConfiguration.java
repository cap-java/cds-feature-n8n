package sap.capire.n8n_plugin;

import com.sap.cds.services.persistence.PersistenceService;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.web.client.RestClient;

// @EnableRetry activates Spring Retry's proxy; without it @Retryable annotations are silently
// ignored
@Configuration
@EnableRetry
@EnableConfigurationProperties(N8nAutoConfiguration.N8nProperties.class)
public class N8nAutoConfiguration {

  // Inner class keeps plugin configuration scoped here rather than polluting the consuming app's
  // config namespace
  @ConfigurationProperties(prefix = "n8n")
  public static class N8nProperties {
    private String baseUrl;
    // webhook-test URLs require manually clicking "Listen for Test Event" in the n8n UI
    // and only fire once — they cannot receive bulk (multi-entry) webhook calls reliably.
    // Set test-base-url + use-test-webhook=true for single-trigger manual testing;
    // keep use-test-webhook=false (default) to use the always-active production webhook URL.
    private String testBaseUrl;
    private boolean useTestWebhook = false;
    private String apiKey = "";

    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public String getTestBaseUrl() {
      return testBaseUrl;
    }

    public void setTestBaseUrl(String testBaseUrl) {
      this.testBaseUrl = testBaseUrl;
    }

    public boolean isUseTestWebhook() {
      return useTestWebhook;
    }

    public void setUseTestWebhook(boolean useTestWebhook) {
      this.useTestWebhook = useTestWebhook;
    }

    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    public String resolvedBaseUrl() {
      if (useTestWebhook) {
        return (testBaseUrl != null && !testBaseUrl.isBlank()) ? testBaseUrl : baseUrl;
      }
      return baseUrl;
    }
  }

  @Bean
  public RestClient n8nRestClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    // Explicit timeouts prevent a slow or unreachable n8n instance from blocking the CAP request
    // thread
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(5000);
    return RestClient.builder().requestFactory(factory).build();
  }

  @Bean
  public N8nWebhookService n8nWebhookService(N8nProperties props, RestClient n8nRestClient) {
    if (props.baseUrl == null || props.baseUrl.isBlank()) {
      throw new IllegalStateException(
          "n8n.base-url must be configured (e.g. http://localhost:5678/webhook)");
    }
    return new N8nWebhookService(props.resolvedBaseUrl(), props.apiKey, n8nRestClient);
  }

  @Bean
  public N8nHandler n8nHandler(N8nWebhookService n8nWebhookService, PersistenceService db) {
    return new N8nHandler(n8nWebhookService, db);
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
