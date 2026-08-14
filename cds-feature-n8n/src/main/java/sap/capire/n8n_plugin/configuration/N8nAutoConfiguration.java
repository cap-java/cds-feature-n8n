/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.configuration;

import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cloud.sdk.cloudplatform.connectivity.DestinationAccessor;
import com.sap.cloud.sdk.cloudplatform.connectivity.Header;
import com.sap.cloud.sdk.cloudplatform.connectivity.HttpDestination;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import sap.capire.n8n_plugin.handlers.N8nHandler;
import sap.capire.n8n_plugin.handlers.N8nOutboxHandler;
import sap.capire.n8n_plugin.handlers.N8nServiceHandler;
import sap.capire.n8n_plugin.services.ConsoleN8NWebhookService;
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

  private static final Logger log = LoggerFactory.getLogger(N8nAutoConfiguration.class);

  /**
   * Typed configuration properties bound from the {@code n8n.*} namespace in {@code
   * application.yaml}.
   */
  @ConfigurationProperties(prefix = "n8n")
  public static class N8nProperties {
    // n8n host only — no /webhook suffix (e.g. http://localhost:5678).
    // The /webhook or /webhook-test prefix is appended automatically based on use-test-webhook.
    private String baseUrl;
    private String apiKey = "";
    private boolean useConsole = false;
    // webhook-test URLs require manually clicking "Listen for Test Event" in the n8n UI
    // and only fire once — they cannot receive bulk (multi-entry) webhook calls reliably.
    // Use for single-trigger manual testing only; keep false (default) for production.
    private boolean useTestWebhook = false;
    private String destination;

    /**
     * @return the n8n host URL without a {@code /webhook} suffix (e.g. {@code
     *     http://localhost:5678})
     */
    public String getBaseUrl() {
      return baseUrl;
    }

    public void setBaseUrl(String baseUrl) {
      this.baseUrl = baseUrl;
    }

    public boolean isUseConsole() {
      return useConsole;
    }

    public void setUseConsole(boolean useConsole) {
      this.useConsole = useConsole;
    }

    public boolean isUseTestWebhook() {
      return useTestWebhook;
    }

    public void setUseTestWebhook(boolean useTestWebhook) {
      this.useTestWebhook = useTestWebhook;
    }

    /**
     * @return the API key sent as {@code X-N8N-API-KEY}
     */
    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    /**
     * @return the BTP destination name; when set, takes priority over {@code baseUrl} and {@code
     *     apiKey}
     */
    public String getDestination() {
      return destination;
    }

    public void setDestination(String destination) {
      this.destination = destination;
    }

    /**
     * Returns the effective webhook base URL with the correct prefix appended: {@code /webhook} for
     * production, {@code /webhook-test} when {@code useTestWebhook} is {@code true}.
     */
    public String resolvedBaseUrl() {
      String prefix = useTestWebhook ? "/webhook-test" : "/webhook";
      String url = baseUrl != null ? baseUrl : "";
      if (url.endsWith("/")) url = url.substring(0, url.length() - 1);
      return url + prefix;
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
   * Console (offline) mode — registered when {@code n8n.use-console=true}.
   *
   * <p>Return type is {@link ConsoleN8NWebhookService} so Spring registers the bean under that
   * concrete type, making {@code @Autowired ConsoleN8NWebhookService} resolvable in tests.
   */
  @Bean
  @ConditionalOnProperty(name = "n8n.use-console", havingValue = "true")
  public ConsoleN8NWebhookService consoleN8nWebhookService() {
    log.warn(
        "n8n.use-console=true — webhook calls will be logged only, no HTTP requests will be made");
    return new ConsoleN8NWebhookService();
  }

  /**
   * HTTP mode — registered when {@code n8n.use-console} is absent or {@code false}.
   *
   * <p>Resolution precedence (mirrors the Node.js plugin):
   *
   * <ol>
   *   <li>{@code n8n.destination} set → resolve via BTP destination service; {@code
   *       cloudplatform-connectivity} must be on the classpath
   *   <li>{@code n8n.base-url} set → uses the configured host + optional API key; {@code /webhook}
   *       or {@code /webhook-test} is appended based on {@code use-test-webhook}
   *   <li>{@code n8n.base-url} missing + {@code development} profile → warns and falls back to
   *       {@code http://localhost:5678}
   *   <li>{@code n8n.base-url} missing + non-dev profile → throws at startup
   * </ol>
   */
  @Bean
  @ConditionalOnProperty(name = "n8n.use-console", havingValue = "false", matchIfMissing = true)
  public N8nWebhookService n8nWebhookService(
      N8nProperties props, RestClient n8nRestClient, Environment environment) {

    // 1) BTP destination — highest priority
    String destinationName = props.getDestination();
    if (destinationName != null && !destinationName.isBlank()) {
      return buildFromDestination(
          destinationName, props.getApiKey(), props.isUseTestWebhook(), n8nRestClient);
    }

    String baseUrl = props.getBaseUrl();
    if (baseUrl != null && !baseUrl.isBlank()) {
      return new N8nWebhookService(
          props.resolvedBaseUrl(), props.getApiKey(), Collections.emptyMap(), n8nRestClient);
    }
    // base-url is missing — behaviour depends on active profile
    if (environment.matchesProfiles("development")) {
      // dev profile: warn and fall back to local n8n; HTTP call fails gracefully if n8n isn't
      // running
      log.warn(
          "n8n.base-url is not set — falling back to http://localhost:5678 for development profile");
      N8nProperties devProps = new N8nProperties();
      devProps.setBaseUrl("http://localhost:5678");
      devProps.setUseTestWebhook(props.isUseTestWebhook());
      return new N8nWebhookService(
          devProps.resolvedBaseUrl(), props.getApiKey(), Collections.emptyMap(), n8nRestClient);
    }
    // non-dev profile: fail fast at startup so misconfiguration is caught immediately
    throw new IllegalStateException(
        "n8n.base-url is not configured. Set the N8N_BASE_URL environment variable, or set n8n.use-console=true for offline mode.");
  }

  private N8nWebhookService buildFromDestination(
      String destinationName,
      String apiKeyOverride,
      boolean useTestWebhook,
      RestClient restClient) {
    try {
      HttpDestination dest = DestinationAccessor.getDestination(destinationName).asHttp();
      String baseUrl = dest.getUri().toString();
      if (baseUrl.endsWith("/")) baseUrl = baseUrl.substring(0, baseUrl.length() - 1);
      baseUrl = baseUrl + (useTestWebhook ? "/webhook-test" : "/webhook");

      Map<String, String> allHeaders = new LinkedHashMap<>();
      for (Header h : dest.getHeaders()) {
        allHeaders.put(h.getName(), h.getValue());
      }

      // API key: explicit override wins, then destination header, then empty
      String apiKey =
          (apiKeyOverride != null && !apiKeyOverride.isBlank())
              ? apiKeyOverride
              : allHeaders.remove("X-N8N-API-KEY");
      if (apiKey == null) apiKey = "";

      log.info("n8n: resolved connection via BTP destination '{}'", destinationName);
      return new N8nWebhookService(baseUrl, apiKey, allHeaders, restClient);
    } catch (NoClassDefFoundError e) {
      throw new IllegalStateException(
          "n8n.destination requires 'cloudplatform-connectivity' on the classpath. "
              + "Add com.sap.cloud.sdk.cloudplatform:cloudplatform-connectivity as a dependency.",
          e);
    } catch (Exception e) {
      throw new IllegalStateException(
          "Failed to resolve n8n BTP destination '" + destinationName + "': " + e.getMessage(), e);
    }
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
