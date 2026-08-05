/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.handlers;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.*;

import com.sap.cds.services.outbox.OutboxMessage;
import com.sap.cds.services.outbox.OutboxMessageEventContext;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import sap.capire.n8n_plugin.services.N8nWebhookService;

@ExtendWith(MockitoExtension.class)
class N8nOutboxHandlerTest {

  @Mock N8nWebhookService webhookService;
  @Mock OutboxMessageEventContext ctx;
  @Mock OutboxMessage message;

  @InjectMocks N8nOutboxHandler handler;

  private void stubCtx(String path, Map<String, Object> payload) {
    when(ctx.getMessage()).thenReturn(message);
    when(message.getParams()).thenReturn(Map.of("path", path, "payload", payload));
  }

  @Test
  void onTrigger_success_marksCompleted() {
    stubCtx("my-webhook", Map.of("ID", "1"));
    doNothing().when(webhookService).notify("my-webhook", Map.of("ID", "1"));

    handler.onTrigger(ctx);

    verify(webhookService).notify("my-webhook", Map.of("ID", "1"));
    verify(ctx).setCompleted();
  }

  @Test
  void onTrigger_resourceAccessException_isRethrown() {
    stubCtx("my-webhook", Map.of("ID", "1"));
    doThrow(new ResourceAccessException("timeout")).when(webhookService).notify(any(), any());

    // outbox must see the exception to trigger retry
    assertThrows(ResourceAccessException.class, () -> handler.onTrigger(ctx));
    verify(ctx, never()).setCompleted();
  }

  static Stream<Class<? extends RuntimeException>> httpErrorTypes() {
    return Stream.of(
        HttpServerErrorException.InternalServerError.class,
        HttpClientErrorException.Unauthorized.class);
  }

  @ParameterizedTest
  @MethodSource("httpErrorTypes")
  void onTrigger_httpError_marksCompletedWithoutRethrow(
      Class<? extends RuntimeException> errorType) {
    stubCtx("my-webhook", Map.of("ID", "1"));
    doThrow(errorType).when(webhookService).notify(any(), any());

    handler.onTrigger(ctx);

    verify(ctx).setCompleted();
  }
}
