/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.configuration;

import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.persistence.PersistenceService;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import sap.capire.n8n_plugin.handlers.N8nHandler;
import sap.capire.n8n_plugin.handlers.N8nOutboxHandler;
import sap.capire.n8n_plugin.handlers.N8nServiceHandler;
import sap.capire.n8n_plugin.services.N8nService;
import sap.capire.n8n_plugin.services.N8nServiceImpl;
import sap.capire.n8n_plugin.services.N8nWebhookService;

/**
 * Spring Boot auto-configuration for the cds-feature-n8n plugin.
 *
 * <p>Registers all plugin beans ({@link sap.capire.n8n_plugin.services.N8nWebhookService}, {@link
 * sap.capire.n8n_plugin.handlers.N8nOutboxHandler}, {@link
 * sap.capire.n8n_plugin.handlers.N8nHandler}, {@link sap.capire.n8n_plugin.services.N8nService},
 * {@link sap.capire.n8n_plugin.handlers.N8nServiceHandler}) and binds plugin configuration from the
 * {@code n8n.*} namespace.
 */
@Configuration
@EnableConfigurationProperties(N8nAutoConfiguration.N8nProperties.class)
public class N8nAutoConfiguration {

  /**
   * Typed configuration properties bound from the {@code n8n.*} namespace in {@code
   * application.yaml}.
   */
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

    /**
     * @return the production webhook base URL (e.g. {@code http://localhost:5678/webhook})
     */
    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    /**
     * @return the test webhook base URL used when {@code useTestWebhook} is {@code true}
     */
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

    /**
     * @return the shared secret sent as {@code X-Webhook-Secret}
     */
    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    /**
     * Returns the effective base URL based on {@code useTestWebhook}. Falls back to {@code baseUrl}
     * when {@code testBaseUrl} is blank.
     */
    public String resolvedBaseUrl() {
      if (useTestWebhook) {
        return (testBaseUrl != null && !testBaseUrl.isBlank()) ? testBaseUrl : baseUrl;
      }
      return baseUrl;
    }
  }

  /**
   * Creates a {@link RestClient} with explicit connect (3 s) and read (5 s) timeouts to prevent a
   * slow or unreachable n8n instance from blocking the CAP request thread.
   */
  @Bean
  public RestClient n8nRestClient() {
    SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
    // Explicit timeouts prevent a slow or unreachable n8n instance from blocking the CAP request
    // thread
    factory.setConnectTimeout(3000);
    factory.setReadTimeout(5000);
    return RestClient.builder().requestFactory(factory).build();
  }

  /**
   * @throws IllegalStateException if {@code n8n.base-url} is not configured
   */
  @Bean
  public N8nWebhookService n8nWebhookService(N8nProperties props, RestClient n8nRestClient) {
    String baseUrl = props.getBaseUrl();
    if (baseUrl == null || baseUrl.isBlank()) {
      throw new IllegalStateException(
          "n8n.base-url must be configured (e.g. http://localhost:5678/webhook)");
    }
    return new N8nWebhookService(props.resolvedBaseUrl(), props.apiKey, n8nRestClient);
  }

  @Bean
  public N8nOutboxHandler n8nOutboxHandler(N8nWebhookService n8nWebhookService) {
    return new N8nOutboxHandler(n8nWebhookService);
  }

  @Bean
  @DependsOn("n8nOutboxHandler")
  public N8nHandler n8nHandler(
      @Qualifier(N8nOutboxHandler.OUTBOX_NAME) OutboxService outbox, PersistenceService db) {
    return new N8nHandler(outbox, db);
  }

  // Return type is the interface so callers depend on the abstraction, not the concrete class
  @Bean
  public N8nService n8nService() {
    return new N8nServiceImpl(N8nService.DEFAULT_NAME);
  }

  @Bean
  @DependsOn("n8nOutboxHandler")
  public N8nServiceHandler n8nServiceHandler(
      @Qualifier(N8nOutboxHandler.OUTBOX_NAME) OutboxService outbox) {
    return new N8nServiceHandler(outbox);
  }
}
