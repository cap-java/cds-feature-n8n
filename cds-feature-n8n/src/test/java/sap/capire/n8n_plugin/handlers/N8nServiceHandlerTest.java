/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.outbox.OutboxMessage;
import com.sap.cds.services.outbox.OutboxService;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import sap.capire.n8n_plugin.configuration.N8nAutoConfiguration.N8nProperties;
import sap.capire.n8n_plugin.services.N8nWebhookService;

@ExtendWith(MockitoExtension.class)
class N8nServiceHandlerTest {

  @Mock OutboxService outbox;
  @Mock EventContext ctx;
  @Mock N8nProperties props;
  @Mock N8nWebhookService webhookService;

  @InjectMocks N8nServiceHandler handler;

  @BeforeEach
  void setUp() {
    lenient().when(props.isUseConsole()).thenReturn(false);
  }

  @Test
  void constructor_nullOutbox_outboxMode_throwsIllegalState() {
    N8nProperties outboxProps = mock(N8nProperties.class);
    when(outboxProps.isUseConsole()).thenReturn(false);
    assertThatThrownBy(() -> new N8nServiceHandler(null, outboxProps, webhookService))
        .isInstanceOf(IllegalStateException.class);
  }

  @Test
  void constructor_nullOutbox_consoleMode_doesNotThrow() {
    N8nProperties consoleProps = mock(N8nProperties.class);
    when(consoleProps.isUseConsole()).thenReturn(true);
    N8nServiceHandler h = new N8nServiceHandler(null, consoleProps, webhookService);
    assertThat(h).isNotNull();
  }

  @Test
  void onTrigger_submitsOutboxMessageAndSetsCompleted() {
    Map<String, Object> payload = Map.of("ID", "42", "title", "Dune");
    when(ctx.get("path")).thenReturn("book-created");
    when(ctx.get("data")).thenReturn(payload);
    when(ctx.get("method")).thenReturn("POST");

    handler.onTrigger(ctx);

    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).submit(eq(N8nOutboxHandler.EVENT_TRIGGER), captor.capture());
    Map<String, Object> params = captor.getValue().getParams();
    assertThat(params).containsEntry("path", "book-created").containsEntry("method", "POST");
    @SuppressWarnings("unchecked")
    Map<String, Object> captured = (Map<String, Object>) params.get("payload");
    assertThat(captured).containsEntry("ID", "42").containsEntry("title", "Dune");
    verify(ctx).setCompleted();
  }

  @Test
  void onTrigger_consoleMode_callsWebhookDirectlyWithoutOutbox() {
    Map<String, Object> payload = Map.of("ID", "42", "title", "Dune");
    when(props.isUseConsole()).thenReturn(true);
    when(ctx.get("path")).thenReturn("book-created");
    when(ctx.get("data")).thenReturn(payload);
    when(ctx.get("method")).thenReturn("POST");

    handler.onTrigger(ctx);

    verify(webhookService).notify("book-created", payload, "POST");
    verify(outbox, never()).submit(any(), any());
    verify(ctx).setCompleted();
  }
}
