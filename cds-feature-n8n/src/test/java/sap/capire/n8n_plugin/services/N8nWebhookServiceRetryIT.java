/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.services;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.github.tomakehurst.wiremock.WireMockServer;
import java.util.Map;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

// WireMock starts a real local HTTP server that impersonates n8n. This lets us verify that
// N8nWebhookService throws the right exception types so the outbox can decide whether to retry:
// - ResourceAccessException (n8n unreachable) → outbox retries
// - HttpStatusCodeException (n8n responded with error) → outbox skips retry (workflow issue)
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = N8nWebhookServiceRetryIT.Config.class)
class N8nWebhookServiceRetryIT {

  static final WireMockServer wireMock = startedServer();

  private static WireMockServer startedServer() {
    WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
    server.start();
    return server;
  }

  @Configuration
  static class Config {
    @Bean
    N8nWebhookService n8nWebhookService() {
      SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
      factory.setConnectTimeout(500);
      factory.setReadTimeout(500);
      RestClient restClient = RestClient.builder().requestFactory(factory).build();
      return new N8nWebhookService(
          "http://localhost:" + wireMock.port(),
          "test-api-key",
          java.util.Collections.emptyMap(),
          restClient);
    }
  }

  @Autowired N8nWebhookService webhookService;

  @BeforeEach
  void resetWireMock() {
    wireMock.resetAll();
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  @Test
  void notify_connectionFailure_throwsResourceAccessException() {
    wireMock.stubFor(
        post(urlEqualTo("/my-webhook"))
            .willReturn(
                aResponse()
                    .withFault(
                        com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

    assertThrows(
        ResourceAccessException.class,
        () -> webhookService.notify("my-webhook", Map.of("event", "created"), HttpMethod.POST));

    wireMock.verify(1, postRequestedFor(urlEqualTo("/my-webhook")));
  }

  @Test
  void notify_success_doesNotThrow() {
    wireMock.stubFor(post(urlEqualTo("/my-webhook")).willReturn(aResponse().withStatus(200)));

    assertDoesNotThrow(
        () -> webhookService.notify("my-webhook", Map.of("event", "created"), HttpMethod.POST));

    wireMock.verify(1, postRequestedFor(urlEqualTo("/my-webhook")));
  }

  @Test
  void notify_workflowError_throwsHttpStatusCodeException() {
    // A 500 from n8n means the workflow itself failed — the outbox will not retry this
    wireMock.stubFor(post(urlEqualTo("/my-webhook")).willReturn(aResponse().withStatus(500)));

    assertThrows(
        HttpStatusCodeException.class,
        () -> webhookService.notify("my-webhook", Map.of("event", "created"), HttpMethod.POST));

    wireMock.verify(1, postRequestedFor(urlEqualTo("/my-webhook")));
  }

  @Test
  void notify_unauthorized_throwsHttpStatusCodeException() {
    wireMock.stubFor(post(urlEqualTo("/my-webhook")).willReturn(aResponse().withStatus(401)));

    assertThrows(
        HttpStatusCodeException.class,
        () -> webhookService.notify("my-webhook", Map.of("event", "created"), HttpMethod.POST));

    wireMock.verify(1, postRequestedFor(urlEqualTo("/my-webhook")));
  }

  @Test
  void notify_putMethod_callsPutEndpoint() {
    wireMock.stubFor(put(urlEqualTo("/my-webhook")).willReturn(aResponse().withStatus(200)));

    assertDoesNotThrow(
        () -> webhookService.notify("my-webhook", Map.of("event", "updated"), HttpMethod.PUT));

    wireMock.verify(1, putRequestedFor(urlEqualTo("/my-webhook")));
  }

  @Test
  void notify_getMethod_sendsPayloadAsQueryParams() {
    wireMock.stubFor(
        get(urlPathEqualTo("/my-webhook"))
            .withQueryParam("id", equalTo("42"))
            .willReturn(aResponse().withStatus(200)));

    assertDoesNotThrow(
        () -> webhookService.notify("my-webhook", Map.of("id", "42"), HttpMethod.GET));

    wireMock.verify(
        1, getRequestedFor(urlPathEqualTo("/my-webhook")).withQueryParam("id", equalTo("42")));
  }

  @Test
  void notify_headMethod_sendsPayloadAsQueryParams() {
    wireMock.stubFor(head(urlPathEqualTo("/my-webhook")).willReturn(aResponse().withStatus(200)));

    assertDoesNotThrow(
        () -> webhookService.notify("my-webhook", Map.of("id", "42"), HttpMethod.HEAD));

    wireMock.verify(
        1, headRequestedFor(urlPathEqualTo("/my-webhook")).withQueryParam("id", equalTo("42")));
  }
}
