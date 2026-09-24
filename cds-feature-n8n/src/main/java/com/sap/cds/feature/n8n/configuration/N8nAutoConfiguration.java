/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package com.sap.cds.feature.n8n.configuration;

import com.sap.cds.feature.n8n.handlers.N8nAnnotationValidator;
import com.sap.cds.feature.n8n.handlers.N8nHandler;
import com.sap.cds.feature.n8n.handlers.N8nOutboxHandler;
import com.sap.cds.feature.n8n.handlers.N8nServiceHandler;
import com.sap.cds.feature.n8n.services.ConsoleN8NWebhookService;
import com.sap.cds.feature.n8n.services.N8nService;
import com.sap.cds.feature.n8n.services.N8nServiceImpl;
import com.sap.cds.feature.n8n.services.N8nWebhookService;
import com.sap.cds.reflect.CdsModel;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.persistence.PersistenceService;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.DependsOn;
import org.springframework.core.env.Environment;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Spring Boot auto-configuration for the cds-feature-n8n plugin.
 *
 * <p>Registers all plugin beans ({@link com.sap.cds.feature.n8n.services.N8nWebhookService}, {@link
 * com.sap.cds.feature.n8n.handlers.N8nOutboxHandler}, {@link
 * com.sap.cds.feature.n8n.handlers.N8nHandler}, {@link
 * com.sap.cds.feature.n8n.services.N8nService}, {@link
 * com.sap.cds.feature.n8n.handlers.N8nServiceHandler}) and binds plugin configuration from the
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
    private WebhookAuth webhookAuth;

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
     * @return the n8n REST API key (used for {@code /api/v1/…} endpoints, not for webhook calls)
     */
    public String getApiKey() {
      return apiKey;
    }

    public void setApiKey(String apiKey) {
      this.apiKey = apiKey;
    }

    /**
     * @return the BTP destination name; when set, takes priority over {@code baseUrl}
     */
    public String getDestination() {
      return destination;
    }

    public void setDestination(String destination) {
      this.destination = destination;
    }

    /**
     * @return the optional webhook authentication configuration
     */
    public WebhookAuth getWebhookAuth() {
      return webhookAuth;
    }

    public void setWebhookAuth(WebhookAuth webhookAuth) {
      this.webhookAuth = webhookAuth;
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

    /**
     * Optional webhook authentication configuration, bound from {@code n8n.webhook-auth.*}.
     *
     * <p>Supported types: {@code basic} (username + password), {@code header} (name + value),
     * {@code bearer} (token). When not set, webhook calls are sent without authentication.
     */
    public static class WebhookAuth {
      private String type;
      private String username;
      private String password;
      private String name;
      private String value;
      private String token;

      public String getType() {
        return type;
      }

      public void setType(String type) {
        this.type = type;
      }

      public String getUsername() {
        return username;
      }

      public void setUsername(String username) {
        this.username = username;
      }

      public String getPassword() {
        return password;
      }

      public void setPassword(String password) {
        this.password = password;
      }

      public String getName() {
        return name;
      }

      public void setName(String name) {
        this.name = name;
      }

      public String getValue() {
        return value;
      }

      public void setValue(String value) {
        this.value = value;
      }

      public String getToken() {
        return token;
      }

      public void setToken(String token) {
        this.token = token;
      }
    }
  }

  /**
   * Resolves the {@code n8n.webhook-auth} configuration into a map of HTTP headers.
   *
   * <ul>
   *   <li>{@code basic} → {@code Authorization: Basic base64(username:password)}
   *   <li>{@code header} → {@code name: value}
   *   <li>{@code bearer} → {@code Authorization: Bearer token}
   *   <li>{@code null} / no type → empty map (no authentication)
   * </ul>
   */
  static Map<String, String> resolveWebhookAuthHeaders(N8nProperties.WebhookAuth auth) {
    if (auth == null || auth.getType() == null) return Collections.emptyMap();
    return switch (auth.getType()) {
      case "basic" -> {
        if (auth.getUsername() == null || auth.getPassword() == null)
          throw new IllegalStateException(
              "n8n.webhook-auth.type=basic requires username and password");
        String encoded =
            Base64.getEncoder()
                .encodeToString(
                    (auth.getUsername() + ":" + auth.getPassword())
                        .getBytes(StandardCharsets.UTF_8));
        yield Map.of("Authorization", "Basic " + encoded);
      }
      case "header" -> {
        if (auth.getName() == null || auth.getValue() == null)
          throw new IllegalStateException("n8n.webhook-auth.type=header requires name and value");
        yield Map.of(auth.getName(), auth.getValue());
      }
      case "bearer" -> {
        if (auth.getToken() == null)
          throw new IllegalStateException("n8n.webhook-auth.type=bearer requires token");
        yield Map.of("Authorization", "Bearer " + auth.getToken());
      }
      default ->
          throw new IllegalStateException("Unsupported n8n.webhook-auth.type: " + auth.getType());
    };
  }

  /**
   * Optional nested configuration that wires an {@link N8nWebhookService} from a BTP destination.
   * Loaded only when {@code cloudplatform-connectivity} is on the classpath — isolates all SDK
   * class references so users without the dependency never trigger a {@link NoClassDefFoundError}.
   */
  @Configuration
  @org.springframework.boot.autoconfigure.condition.ConditionalOnClass(
      name = "com.sap.cloud.sdk.cloudplatform.connectivity.DestinationAccessor")
  @ConditionalOnProperty(name = "n8n.destination")
  @ConditionalOnProperty(name = "n8n.use-console", havingValue = "false", matchIfMissing = true)
  public static class DestinationConfiguration {

    private static final Logger log = LoggerFactory.getLogger(DestinationConfiguration.class);

    /**
     * Resolves {@code n8n.destination} via the SAP Cloud SDK and creates an {@link
     * N8nWebhookService} with the destination's URL and auth headers.
     *
     * <ul>
     *   <li>The destination URI plus {@code /webhook} (or {@code /webhook-test}) becomes the base
     *       URL.
     *   <li>All destination headers except {@code X-N8N-API-KEY} are forwarded as auth headers
     *       ({@code X-N8N-API-KEY} is a REST API credential and must not be sent to webhook nodes).
     *   <li>{@code n8n.webhook-auth} config headers are merged on top (override destination headers
     *       for the same header name).
     * </ul>
     */
    @Bean
    @ConditionalOnMissingBean(N8nWebhookService.class)
    public N8nWebhookService n8nWebhookServiceFromDestination(
        N8nProperties props, RestClient n8nRestClient) {

      com.sap.cloud.sdk.cloudplatform.connectivity.HttpDestination dest;
      try {
        dest =
            com.sap.cloud.sdk.cloudplatform.connectivity.DestinationAccessor.getDestination(
                    props.getDestination())
                .asHttp();
      } catch (Exception e) {
        throw new IllegalStateException(
            "Failed to resolve n8n BTP destination '"
                + props.getDestination()
                + "': "
                + e.getMessage(),
            e);
      }

      String rawUrl = dest.getUri().toString();
      if (rawUrl.endsWith("/")) rawUrl = rawUrl.substring(0, rawUrl.length() - 1);
      String baseUrl = rawUrl + (props.isUseTestWebhook() ? "/webhook-test" : "/webhook");

      Map<String, String> authHeaders = new LinkedHashMap<>();
      for (com.sap.cloud.sdk.cloudplatform.connectivity.Header h : dest.getHeaders()) {
        // X-N8N-API-KEY is the REST API credential — do not forward it to webhook nodes
        if (!h.getName().equalsIgnoreCase("X-N8N-API-KEY")) {
          authHeaders.put(h.getName(), h.getValue());
        }
      }
      // webhook-auth config overrides destination headers for the same header name
      authHeaders.putAll(resolveWebhookAuthHeaders(props.getWebhookAuth()));

      log.info("n8n: resolved connection via BTP destination '{}'", props.getDestination());
      return new N8nWebhookService(baseUrl, authHeaders, n8nRestClient);
    }
  }

  // ---------------------------------------------------------------------------
  // Infrastructure — always registered regardless of mode
  // ---------------------------------------------------------------------------

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

  // Return type is the interface so callers depend on the abstraction, not the concrete class
  @Bean
  public N8nService n8nService() {
    return new N8nServiceImpl(N8nService.DEFAULT_NAME);
  }

  // ---------------------------------------------------------------------------
  // WebhookService — exactly one of the three variants below is registered
  // ---------------------------------------------------------------------------

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
   * HTTP mode — registered when {@code n8n.use-console} is absent or {@code false} and no
   * destination-based bean was already registered by {@link DestinationConfiguration}.
   *
   * <ul>
   *   <li>{@code n8n.base-url} set → uses the configured host with webhook auth from {@code
   *       n8n.webhook-auth}; {@code /webhook} or {@code /webhook-test} is appended based on {@code
   *       use-test-webhook}
   *   <li>{@code n8n.base-url} missing + {@code development} profile → warns and falls back to
   *       {@code http://localhost:5678}
   *   <li>{@code n8n.base-url} missing + non-dev profile → throws at startup
   * </ul>
   */
  @Bean
  @ConditionalOnProperty(name = "n8n.use-console", havingValue = "false", matchIfMissing = true)
  @ConditionalOnMissingBean(N8nWebhookService.class)
  public N8nWebhookService n8nWebhookService(
      N8nProperties props, RestClient n8nRestClient, Environment environment) {

    Map<String, String> webhookAuthHeaders = resolveWebhookAuthHeaders(props.getWebhookAuth());

    String baseUrl = props.getBaseUrl();
    if (baseUrl != null && !baseUrl.isBlank()) {
      return new N8nWebhookService(props.resolvedBaseUrl(), webhookAuthHeaders, n8nRestClient);
    }
    // base-url is missing — behaviour depends on active profile
    if (environment.matchesProfiles("development")) {
      log.warn(
          "n8n.base-url is not set — falling back to http://localhost:5678 for development profile");
      N8nProperties devProps = new N8nProperties();
      devProps.setBaseUrl("http://localhost:5678");
      devProps.setUseTestWebhook(props.isUseTestWebhook());
      return new N8nWebhookService(devProps.resolvedBaseUrl(), webhookAuthHeaders, n8nRestClient);
    }
    // non-dev profile: fail fast at startup so misconfiguration is caught immediately
    throw new IllegalStateException(
        "n8n.base-url is not configured. Set the N8N_BASE_URL environment variable, or set n8n.use-console=true for offline mode.");
  }

  // ---------------------------------------------------------------------------
  // Outbox mode (n8n.use-console=false, the default)
  // Webhooks are submitted to the persistent outbox and delivered after commit.
  // ---------------------------------------------------------------------------

  @Bean
  @ConditionalOnProperty(name = "n8n.use-console", havingValue = "false", matchIfMissing = true)
  public N8nOutboxHandler n8nOutboxHandler(N8nWebhookService n8nWebhookService) {
    return new N8nOutboxHandler(n8nWebhookService);
  }

  @Bean
  @DependsOn("n8nOutboxHandler")
  @ConditionalOnProperty(name = "n8n.use-console", havingValue = "false", matchIfMissing = true)
  public N8nHandler n8nHandler(
      @Qualifier(N8nOutboxHandler.OUTBOX_NAME) OutboxService outbox,
      PersistenceService db,
      N8nProperties props,
      N8nWebhookService webhookService) {
    return new N8nHandler(outbox, db, props, webhookService);
  }

  @Bean
  public N8nAnnotationValidator n8nAnnotationValidator(CdsModel cdsModel) {
    return new N8nAnnotationValidator(cdsModel);
  }

  @Bean
  @DependsOn("n8nOutboxHandler")
  @ConditionalOnProperty(name = "n8n.use-console", havingValue = "false", matchIfMissing = true)
  public N8nServiceHandler n8nServiceHandler(
      @Qualifier(N8nOutboxHandler.OUTBOX_NAME) OutboxService outbox,
      N8nProperties props,
      N8nWebhookService webhookService) {
    return new N8nServiceHandler(outbox, props, webhookService);
  }

  // ---------------------------------------------------------------------------
  // Console mode (n8n.use-console=true)
  // Webhooks are delivered synchronously and logged — no outbox infrastructure needed.
  // ---------------------------------------------------------------------------

  @Bean
  @ConditionalOnProperty(name = "n8n.use-console", havingValue = "true")
  public N8nHandler consoleN8nHandler(
      PersistenceService db, N8nProperties props, N8nWebhookService webhookService) {
    return new N8nHandler(null, db, props, webhookService);
  }

  @Bean
  @ConditionalOnProperty(name = "n8n.use-console", havingValue = "true")
  public N8nServiceHandler consoleN8nServiceHandler(
      N8nProperties props, N8nWebhookService webhookService) {
    return new N8nServiceHandler(null, props, webhookService);
  }
}
