package sap.capire.n8n_plugin;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.stubbing.Scenario;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.retry.annotation.EnableRetry;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.util.Map;

import static com.github.tomakehurst.wiremock.client.WireMock.*;
import static com.github.tomakehurst.wiremock.core.WireMockConfiguration.wireMockConfig;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

// WireMock starts a real local HTTP server that impersonates n8n. This lets us verify retry
// behaviour end-to-end: RestClient makes actual HTTP calls, WireMock returns controlled responses,
// and Spring Retry drives the retry loop — no mocking of RestClient internals needed.
@ExtendWith(SpringExtension.class)
@ContextConfiguration(classes = N8nWebhookServiceRetryIT.Config.class)
@TestPropertySource(properties = {
    "n8n.retry.max-attempts=3",
    "n8n.retry.delay=1",
    "n8n.retry.multiplier=1"
})
class N8nWebhookServiceRetryIT {

    // Started in a static initializer so it's up before the Spring context builds the N8nWebhookService bean.
    static final WireMockServer wireMock = startedServer();

    private static WireMockServer startedServer() {
        WireMockServer server = new WireMockServer(wireMockConfig().dynamicPort());
        server.start();
        return server;
    }

    @Configuration
    @EnableRetry
    static class Config {
        @Bean
        N8nWebhookService n8nWebhookService() {
            SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
            factory.setConnectTimeout(500);
            factory.setReadTimeout(500);
            RestClient restClient = RestClient.builder()
                .requestFactory(factory)
                .build();
            return new N8nWebhookService(
                "http://localhost:" + wireMock.port(),
                "test-api-key",
                restClient
            );
        }
    }

    @Autowired
    N8nWebhookService webhookService;

    @BeforeEach
    void resetWireMock() {
        wireMock.resetAll();
    }

    @AfterAll
    static void stopWireMock() {
        wireMock.stop();
    }

    @Test
    void notify_retriesOnConnectionFailure_succeedsOnThirdAttempt() {
        // Simulate n8n briefly unreachable: drop the connection twice, then respond 200
        wireMock.stubFor(post(urlEqualTo("/my-webhook"))
            .inScenario("retry")
            .whenScenarioStateIs(Scenario.STARTED)
            .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER))
            .willSetStateTo("fail-1"));

        wireMock.stubFor(post(urlEqualTo("/my-webhook"))
            .inScenario("retry")
            .whenScenarioStateIs("fail-1")
            .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER))
            .willSetStateTo("fail-2"));

        wireMock.stubFor(post(urlEqualTo("/my-webhook"))
            .inScenario("retry")
            .whenScenarioStateIs("fail-2")
            .willReturn(aResponse().withStatus(200)));

        assertDoesNotThrow(() -> webhookService.notify("my-webhook", Map.of("event", "created")));

        wireMock.verify(3, postRequestedFor(urlEqualTo("/my-webhook")));
    }

    @Test
    void notify_allAttemptsFailWithConnectionError_recoverHandlesWithoutPropagating() {
        wireMock.stubFor(post(urlEqualTo("/my-webhook"))
            .willReturn(aResponse().withFault(com.github.tomakehurst.wiremock.http.Fault.CONNECTION_RESET_BY_PEER)));

        assertDoesNotThrow(() -> webhookService.notify("my-webhook", Map.of("event", "created")));

        wireMock.verify(3, postRequestedFor(urlEqualTo("/my-webhook")));
    }

    @Test
    void notify_workflowError_notRetried() {
        // A 500 from n8n means the workflow itself failed — retrying is pointless
        wireMock.stubFor(post(urlEqualTo("/my-webhook"))
            .willReturn(aResponse().withStatus(500)));

        assertDoesNotThrow(() -> webhookService.notify("my-webhook", Map.of("event", "created")));

        // Must be called exactly once — no retries
        wireMock.verify(1, postRequestedFor(urlEqualTo("/my-webhook")));
    }

    @Test
    void notify_unauthorized_notRetried() {
        wireMock.stubFor(post(urlEqualTo("/my-webhook"))
            .willReturn(aResponse().withStatus(401)));

        assertDoesNotThrow(() -> webhookService.notify("my-webhook", Map.of("event", "created")));

        wireMock.verify(1, postRequestedFor(urlEqualTo("/my-webhook")));
    }
}
