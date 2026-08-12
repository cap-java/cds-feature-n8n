/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
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
    N8nProperties props = propsWithBaseUrl("http://localhost:5678/webhook");
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
                propsWithBaseUrl("http://localhost:5678/webhook-test"),
                mock(RestClient.class),
                mockEnv()));
  }

  // --- resolvedBaseUrl ---

  @Test
  void resolvedBaseUrl_useTestWebhookFalse_returnsBaseUrl() {
    N8nProperties props = propsWithBaseUrl("http://prod/webhook");
    props.setTestBaseUrl("http://test/webhook-test");
    assertThat(props.resolvedBaseUrl()).isEqualTo("http://prod/webhook");
  }

  @Test
  void resolvedBaseUrl_useTestWebhookTrue_returnsTestBaseUrl() {
    N8nProperties props = propsWithBaseUrl("http://prod/webhook");
    props.setTestBaseUrl("http://test/webhook-test");
    props.setUseTestWebhook(true);
    assertThat(props.resolvedBaseUrl()).isEqualTo("http://test/webhook-test");
  }

  @Test
  void resolvedBaseUrl_useTestWebhookTrue_testBaseUrlBlank_fallsBackToBaseUrl() {
    N8nProperties props = propsWithBaseUrl("http://prod/webhook");
    props.setTestBaseUrl("  ");
    props.setUseTestWebhook(true);
    assertThat(props.resolvedBaseUrl()).isEqualTo("http://prod/webhook");
  }

  @Test
  void resolvedBaseUrl_useTestWebhookTrue_testBaseUrlNull_fallsBackToBaseUrl() {
    N8nProperties props = propsWithBaseUrl("http://prod/webhook");
    props.setUseTestWebhook(true);
    assertThat(props.resolvedBaseUrl()).isEqualTo("http://prod/webhook");
  }
}
