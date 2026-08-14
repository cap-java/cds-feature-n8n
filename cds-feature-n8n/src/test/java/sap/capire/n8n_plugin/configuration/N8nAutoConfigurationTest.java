/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;

import com.sap.cloud.sdk.cloudplatform.connectivity.Destination;
import com.sap.cloud.sdk.cloudplatform.connectivity.DestinationAccessor;
import com.sap.cloud.sdk.cloudplatform.connectivity.Header;
import com.sap.cloud.sdk.cloudplatform.connectivity.HttpDestination;
import java.net.URI;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.web.client.RestClient;
import sap.capire.n8n_plugin.configuration.N8nAutoConfiguration.N8nProperties;
import sap.capire.n8n_plugin.services.ConsoleN8NWebhookService;
import sap.capire.n8n_plugin.services.N8nWebhookService;

class N8nAutoConfigurationTest {

  private final N8nAutoConfiguration config = new N8nAutoConfiguration();

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
    N8nProperties props = new N8nProperties();
    props.setUseConsole(true);
    ConsoleN8NWebhookService bean = config.consoleN8nWebhookService();
    assertThat(bean).isInstanceOf(ConsoleN8NWebhookService.class);
  }

  @Test
  void useConsole_false_withBaseUrl_createsRealWebhookService() {
    N8nProperties props = propsWithBaseUrl("http://localhost:5678");
    N8nWebhookService bean = config.n8nWebhookService(props, mock(RestClient.class), mockEnv());
    assertThat(bean).isNotInstanceOf(ConsoleN8NWebhookService.class);
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
  void destination_set_resolves_baseUrlAndAuthHeadersFromDestination() {
    HttpDestination mockDest = mock(HttpDestination.class);
    Destination mockDestWrapper = mock(Destination.class);
    org.mockito.Mockito.when(mockDest.getUri()).thenReturn(URI.create("https://n8n.example.com"));
    org.mockito.Mockito.when(mockDest.getHeaders())
        .thenReturn(List.of(new Header("Authorization", "Bearer test-token")));
    org.mockito.Mockito.when(mockDestWrapper.asHttp()).thenReturn(mockDest);

    try (MockedStatic<DestinationAccessor> accessor = mockStatic(DestinationAccessor.class)) {
      accessor
          .when(() -> DestinationAccessor.getDestination("my-dest"))
          .thenReturn(mockDestWrapper);

      N8nProperties props = propsWithDestination("my-dest");
      N8nWebhookService bean = config.n8nWebhookService(props, mock(RestClient.class), mockEnv());
      assertThat(bean).isNotNull();
    }
  }

  @Test
  void destination_set_apiKeyOverride_takesPreference() {
    HttpDestination mockDest = mock(HttpDestination.class);
    Destination mockDestWrapper = mock(Destination.class);
    org.mockito.Mockito.when(mockDest.getUri()).thenReturn(URI.create("https://n8n.example.com"));
    org.mockito.Mockito.when(mockDest.getHeaders())
        .thenReturn(List.of(new Header("X-N8N-API-KEY", "from-destination")));
    org.mockito.Mockito.when(mockDestWrapper.asHttp()).thenReturn(mockDest);

    try (MockedStatic<DestinationAccessor> accessor = mockStatic(DestinationAccessor.class)) {
      accessor
          .when(() -> DestinationAccessor.getDestination("my-dest"))
          .thenReturn(mockDestWrapper);

      N8nProperties props = propsWithDestination("my-dest");
      props.setApiKey("explicit-override");
      N8nWebhookService bean = config.n8nWebhookService(props, mock(RestClient.class), mockEnv());
      assertThat(bean).isNotNull();
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
              () -> config.n8nWebhookService(props, mock(RestClient.class), mockEnv()));
      assertThat(ex.getMessage()).contains("missing-dest");
    }
  }
}
