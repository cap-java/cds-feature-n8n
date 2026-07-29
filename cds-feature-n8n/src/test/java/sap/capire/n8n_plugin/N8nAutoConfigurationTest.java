package sap.capire.n8n_plugin;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;

import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

class N8nAutoConfigurationTest {

    private final N8nAutoConfiguration config = new N8nAutoConfiguration();

    private N8nAutoConfiguration.N8nProperties propsWithBaseUrl(String baseUrl) {
        N8nAutoConfiguration.N8nProperties props = new N8nAutoConfiguration.N8nProperties();
        props.setBaseUrl(baseUrl);
        return props;
    }

    @Test
    void n8nWebhookService_throwsWhenBaseUrlIsNull() {
        N8nAutoConfiguration.N8nProperties props = new N8nAutoConfiguration.N8nProperties();
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> config.n8nWebhookService(props, mock(RestClient.class)));
        assertTrue(ex.getMessage().contains("n8n.base-url"));
    }

    @Test
    void n8nWebhookService_throwsWhenBaseUrlIsBlank() {
        IllegalStateException ex = assertThrows(IllegalStateException.class,
            () -> config.n8nWebhookService(propsWithBaseUrl("  "), mock(RestClient.class)));
        assertTrue(ex.getMessage().contains("n8n.base-url"));
    }

    @Test
    void n8nWebhookService_createsBean_whenBaseUrlIsSet() {
        assertDoesNotThrow(() ->
            config.n8nWebhookService(
                propsWithBaseUrl("http://localhost:5678/webhook-test"),
                mock(RestClient.class)));
    }
}
