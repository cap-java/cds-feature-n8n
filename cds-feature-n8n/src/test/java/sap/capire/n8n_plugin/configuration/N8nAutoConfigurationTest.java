/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cloud.sdk.cloudplatform.connectivity.Destination;
import com.sap.cloud.sdk.cloudplatform.connectivity.DestinationAccessor;
import com.sap.cloud.sdk.cloudplatform.connectivity.Header;
import com.sap.cloud.sdk.cloudplatform.connectivity.HttpDestination;
import java.lang.reflect.Field;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;
import sap.capire.n8n_plugin.configuration.N8nAutoConfiguration.DestinationConfiguration;
import sap.capire.n8n_plugin.configuration.N8nAutoConfiguration.N8nProperties;
import sap.capire.n8n_plugin.handlers.N8nHandler;
import sap.capire.n8n_plugin.handlers.N8nServiceHandler;
import sap.capire.n8n_plugin.services.ConsoleN8NWebhookService;
import sap.capire.n8n_plugin.services.N8nWebhookService;

class N8nAutoConfigurationTest {

  private final N8nAutoConfiguration config = new N8nAutoConfiguration();
  private final DestinationConfiguration destConfig = new DestinationConfiguration();

  @SuppressWarnings("unchecked")
  private static <T> T field(Object obj, String name) throws Exception {
    Field f = obj.getClass().getDeclaredField(name);
    f.setAccessible(true);
    return (T) f.get(obj);
  }

  private N8nProperties propsWithBaseUrl(String baseUrl) {
    N8nProperties props = new N8nProperties();
    props.setBaseUrl(baseUrl);
    return props;
  }

  private N8nProperties propsWithDestination(String destinationName) {
    N8nProperties props = new N8nProperties();
    props.setDestination(destinationName);
    return props;
  }

  private MockEnvironment mockEnv(String... activeProfiles) {
    MockEnvironment env = new MockEnvironment();
    env.setActiveProfiles(activeProfiles);
    return env;
  }

  // --- useConsole wiring ---

  @Test
  void useConsole_true_createsConsoleWebhookService() {
    ConsoleN8NWebhookService bean = config.consoleN8nWebhookService();
    assertThat(bean).isInstanceOf(ConsoleN8NWebhookService.class);
  }

  @Test
  void useConsole_false_withBaseUrl_createsRealWebhookService() {
    N8nProperties props = propsWithBaseUrl("http://localhost:5678");
    N8nWebhookService bean = config.n8nWebhookService(props, mock(RestClient.class), mockEnv());
    assertThat(bean).isNotInstanceOf(ConsoleN8NWebhookService.class);
  }

  @Test
  void useConsole_true_consoleN8nHandler_doesNotRequireOutbox() {
    N8nProperties props = new N8nProperties();
    props.setUseConsole(true);
    N8nHandler handler =
        config.consoleN8nHandler(
            mock(PersistenceService.class), props, new ConsoleN8NWebhookService());
    assertThat(handler).isNotNull();
  }

  @Test
  void useConsole_true_consoleN8nServiceHandler_doesNotRequireOutbox() {
    N8nProperties props = new N8nProperties();
    props.setUseConsole(true);
    N8nServiceHandler handler =
        config.consoleN8nServiceHandler(props, new ConsoleN8NWebhookService());
    assertThat(handler).isNotNull();
  }

  // --- missing base-url: profile-aware behaviour ---

  @Test
  void noBaseUrl_nonDevProfile_throwsAtStartup() {
    N8nProperties props = new N8nProperties();
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> config.n8nWebhookService(props, mock(RestClient.class), mockEnv()));
    assertThat(ex.getMessage()).contains("N8N_BASE_URL");
  }

  @Test
  void noBaseUrl_devProfile_doesNotThrow() {
    N8nProperties props = new N8nProperties();
    assertDoesNotThrow(
        () -> config.n8nWebhookService(props, mock(RestClient.class), mockEnv("development")));
  }

  @Test
  void noBaseUrl_devProfile_createsRealNotConsoleService() {
    N8nProperties props = new N8nProperties();
    N8nWebhookService bean =
        config.n8nWebhookService(props, mock(RestClient.class), mockEnv("development"));
    assertThat(bean).isNotInstanceOf(ConsoleN8NWebhookService.class);
  }

  // --- n8nWebhookService factory ---

  @Test
  void n8nWebhookService_createsBean_whenBaseUrlIsSet() {
    assertDoesNotThrow(
        () ->
            config.n8nWebhookService(
                propsWithBaseUrl("http://localhost:5678"), mock(RestClient.class), mockEnv()));
  }

  // --- resolvedBaseUrl ---

  @Test
  void resolvedBaseUrl_useTestWebhookFalse_appendsWebhookPrefix() {
    N8nProperties props = propsWithBaseUrl("http://n8n.example.com");
    assertThat(props.resolvedBaseUrl()).isEqualTo("http://n8n.example.com/webhook");
  }

  @Test
  void resolvedBaseUrl_useTestWebhookTrue_appendsWebhookTestPrefix() {
    N8nProperties props = propsWithBaseUrl("http://n8n.example.com");
    props.setUseTestWebhook(true);
    assertThat(props.resolvedBaseUrl()).isEqualTo("http://n8n.example.com/webhook-test");
  }

  @Test
  void resolvedBaseUrl_trailingSlashStripped() {
    N8nProperties props = propsWithBaseUrl("http://n8n.example.com/");
    assertThat(props.resolvedBaseUrl()).isEqualTo("http://n8n.example.com/webhook");
  }

  // --- BTP destination ---

  @Test
  void destination_set_resolves_baseUrlAndAuthHeadersFromDestination() throws Exception {
    HttpDestination mockDest = mock(HttpDestination.class);
    Destination mockDestWrapper = mock(Destination.class);
    when(mockDest.getUri()).thenReturn(URI.create("https://n8n.example.com"));
    when(mockDest.getHeaders())
        .thenReturn(List.of(new Header("Authorization", "Bearer test-token")));
    when(mockDestWrapper.asHttp()).thenReturn(mockDest);

    try (MockedStatic<DestinationAccessor> accessor = mockStatic(DestinationAccessor.class)) {
      accessor
          .when(() -> DestinationAccessor.getDestination("my-dest"))
          .thenReturn(mockDestWrapper);

      N8nWebhookService bean =
          destConfig.n8nWebhookServiceFromDestination(
              propsWithDestination("my-dest"), mock(RestClient.class));

      assertThat((String) field(bean, "baseUrl")).isEqualTo("https://n8n.example.com/webhook");
      assertThat((String) field(bean, "apiKey")).isEmpty();
      assertThat((Map<String, String>) field(bean, "authHeaders"))
          .containsEntry("Authorization", "Bearer test-token");
    }
  }

  @Test
  void destination_set_apiKeyOverride_takesPreference() throws Exception {
    HttpDestination mockDest = mock(HttpDestination.class);
    Destination mockDestWrapper = mock(Destination.class);
    when(mockDest.getUri()).thenReturn(URI.create("https://n8n.example.com"));
    when(mockDest.getHeaders())
        .thenReturn(
            List.of(
                new Header("Authorization", "Bearer test-token"),
                new Header("X-N8N-API-KEY", "from-destination")));
    when(mockDestWrapper.asHttp()).thenReturn(mockDest);

    try (MockedStatic<DestinationAccessor> accessor = mockStatic(DestinationAccessor.class)) {
      accessor
          .when(() -> DestinationAccessor.getDestination("my-dest"))
          .thenReturn(mockDestWrapper);

      N8nProperties props = propsWithDestination("my-dest");
      props.setApiKey("explicit-override");

      N8nWebhookService bean =
          destConfig.n8nWebhookServiceFromDestination(props, mock(RestClient.class));

      assertThat((String) field(bean, "apiKey")).isEqualTo("explicit-override");
      // X-N8N-API-KEY from the destination must not leak into authHeaders
      assertThat((Map<String, String>) field(bean, "authHeaders"))
          .doesNotContainKey("X-N8N-API-KEY")
          .containsEntry("Authorization", "Bearer test-token");
    }
  }

  @Test
  void destination_set_destinationNotFound_throwsAtStartup() {
    try (MockedStatic<DestinationAccessor> accessor = mockStatic(DestinationAccessor.class)) {
      accessor
          .when(() -> DestinationAccessor.getDestination("missing-dest"))
          .thenThrow(new RuntimeException("Destination not found"));

      N8nProperties props = propsWithDestination("missing-dest");
      IllegalStateException ex =
          assertThrows(
              IllegalStateException.class,
              () -> destConfig.n8nWebhookServiceFromDestination(props, mock(RestClient.class)));
      assertThat(ex.getMessage()).contains("missing-dest");
    }
  }
}
