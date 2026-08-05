/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.outbox.OutboxMessage;
import com.sap.cds.services.outbox.OutboxService;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class N8nServiceHandlerTest {

  @Mock OutboxService outbox;
  @Mock EventContext ctx;

  @InjectMocks N8nServiceHandler handler;

  @Test
  void onTrigger_submitsOutboxMessageAndSetsCompleted() {
    Map<String, Object> payload = Map.of("ID", "42", "title", "Dune");
    when(ctx.get("path")).thenReturn("book-created");
    when(ctx.get("data")).thenReturn(payload);

    handler.onTrigger(ctx);

    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).submit(eq(N8nOutboxHandler.EVENT_TRIGGER), captor.capture());
    Map<String, Object> params = captor.getValue().getParams();
    assertThat(params).containsEntry("path", "book-created");
    @SuppressWarnings("unchecked")
    Map<String, Object> captured = (Map<String, Object>) params.get("payload");
    assertThat(captured).containsEntry("ID", "42").containsEntry("title", "Dune");
    verify(ctx).setCompleted();
  }
}
