/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.configuration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;
import sap.capire.n8n_plugin.configuration.N8nAutoConfiguration.N8nProperties;

class N8nAutoConfigurationTest {

  private final N8nAutoConfiguration config = new N8nAutoConfiguration();

  private N8nProperties propsWithBaseUrl(String baseUrl) {
    N8nProperties props = new N8nProperties();
    props.setBaseUrl(baseUrl);
    return props;
  }

  @Test
  void n8nWebhookService_throwsWhenBaseUrlIsNull() {
    N8nProperties props = new N8nProperties();
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> config.n8nWebhookService(props, mock(RestClient.class)));
    assertTrue(ex.getMessage().contains("n8n.base-url"));
  }

  @Test
  void n8nWebhookService_throwsWhenBaseUrlIsBlank() {
    IllegalStateException ex =
        assertThrows(
            IllegalStateException.class,
            () -> config.n8nWebhookService(propsWithBaseUrl("  "), mock(RestClient.class)));
    assertTrue(ex.getMessage().contains("n8n.base-url"));
  }

  @Test
  void n8nWebhookService_createsBean_whenBaseUrlIsSet() {
    assertDoesNotThrow(
        () ->
            config.n8nWebhookService(
                propsWithBaseUrl("http://localhost:5678/webhook-test"), mock(RestClient.class)));
  }

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
