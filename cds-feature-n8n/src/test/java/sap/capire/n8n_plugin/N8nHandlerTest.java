package sap.capire.n8n_plugin;

import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsDeleteEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.outbox.OutboxMessage;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.persistence.PersistenceService;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnUpdate;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class N8nHandlerTest {

    @Mock
    OutboxService outbox;

    @Mock
    PersistenceService db;

    @Mock
    CdsCreateEventContext createCtx;

    @Mock
    CdsDeleteEventContext deleteCtx;

    @Mock
    CdsUpdateEventContext updateCtx;

    @Mock
    EventContext eventCtx;

    @Mock
    CdsEntity entity;

    @Mock
    CqnInsert cqnInsert;

    @Mock
    CqnUpdate cqnUpdate;

    @InjectMocks
    N8nHandler handler;

    @SuppressWarnings("unchecked")
    private Map<String, Object> capturePayload() {
        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).submit(eq(N8nOutboxHandler.EVENT_TRIGGER), captor.capture());
        return (Map<String, Object>) captor.getValue().getParams().get("payload");
    }

    @SuppressWarnings("unchecked")
    private List<Map<String, Object>> captureAllPayloads() {
        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox, atLeastOnce()).submit(eq(N8nOutboxHandler.EVENT_TRIGGER), captor.capture());
        return captor.getAllValues().stream()
            .map(m -> (Map<String, Object>) m.getParams().get("payload"))
            .toList();
    }

    @Test
    void onCreate_withAnnotation_notifiesWebhook() {
        when(createCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "CREATE", "path", "book-created", "inputs", List.of("ID", "title"))));
        when(createCtx.getEvent()).thenReturn("CREATE");
        when(createCtx.getCqn()).thenReturn(cqnInsert);
        when(cqnInsert.entries()).thenReturn(List.of(Map.of("ID", "1", "title", "Dune")));

        handler.afterCrudEvent(createCtx);

        Map<String, Object> payload = capturePayload();
        assertThat(payload).containsEntry("ID", "1").containsEntry("title", "Dune");
    }

    @Test
    void onCreate_bulkInsert_notifiesOncePerEntry() {
        when(createCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "CREATE", "path", "book-created", "inputs", List.of("ID", "title"))));
        when(createCtx.getEvent()).thenReturn("CREATE");
        when(createCtx.getCqn()).thenReturn(cqnInsert);
        when(cqnInsert.entries()).thenReturn(List.of(
            Map.of("ID", "1", "title", "Dune"),
            Map.of("ID", "2", "title", "Dune Messiah"),
            Map.of("ID", "3", "title", "Children of Dune")
        ));

        handler.afterCrudEvent(createCtx);

        List<Map<String, Object>> payloads = captureAllPayloads();
        assertThat(payloads).hasSize(3);
        assertThat(payloads).anyMatch(p -> "1".equals(p.get("ID")));
        assertThat(payloads).anyMatch(p -> "2".equals(p.get("ID")));
        assertThat(payloads).anyMatch(p -> "3".equals(p.get("ID")));
    }

    @Test
    void onCreate_emptyEntries_doesNotNotify() {
        when(createCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "CREATE", "path", "book-created")));
        when(createCtx.getEvent()).thenReturn("CREATE");
        when(createCtx.getCqn()).thenReturn(cqnInsert);
        when(cqnInsert.entries()).thenReturn(List.of());

        handler.afterCrudEvent(createCtx);

        verify(outbox, never()).submit(any(), any());
    }

    @Test
    void afterCreate_withoutAnnotation_doesNotNotify() {
        when(createCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of());

        handler.afterCrudEvent(createCtx);

        verify(outbox, never()).submit(any(), any());
    }

    @Test
    void afterCreate_nullTarget_doesNotNotify() {
        when(createCtx.getTarget()).thenReturn(null);

        handler.afterCrudEvent(createCtx);

        verify(outbox, never()).submit(any(), any());
    }

    @Test
    void afterCreate_annotationForDifferentEvent_doesNotNotify() {
        when(createCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "DELETE", "path", "book-deleted")));
        when(createCtx.getEvent()).thenReturn("CREATE");

        handler.afterCrudEvent(createCtx);

        verify(outbox, never()).submit(any(), any());
    }

    @Test
    void onDelete_withAnnotation_prefetchesAndNotifiesWebhook() {
        N8nHandler handlerForDelete = new N8nHandler(outbox, db) {
            @Override
            protected Map<String, Object> extractKeys(EventContext ctx) {
                return Map.of("ID", "some-uuid");
            }
            @Override
            protected Map<String, Object> fetchEntityRow(EventContext ctx, Map<String, Object> keys) {
                return Map.of("ID", "some-uuid", "title", "Dune", "author_ID", "author-1");
            }
        };

        when(deleteCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "DELETE", "path", "book-deleted", "inputs", List.of("ID", "title", "author_ID"))));
        when(deleteCtx.getEvent()).thenReturn("DELETE");
        when(deleteCtx.get("n8n.prefetch")).thenReturn(Map.of("ID", "some-uuid", "title", "Dune", "author_ID", "author-1"));

        handlerForDelete.beforeMutatingEvent(deleteCtx);
        handlerForDelete.afterCrudEvent(deleteCtx);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).submit(eq(N8nOutboxHandler.EVENT_TRIGGER), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) captor.getValue().getParams().get("payload");
        assertThat(payload)
            .containsEntry("ID", "some-uuid")
            .containsEntry("title", "Dune")
            .containsEntry("author_ID", "author-1");
    }

    // PATCH only sends changed fields; n8n should still receive the full post-update row
    @Test
    void onUpdate_withAnnotation_mergesDeltaOverPrefetchedRow() {
        N8nHandler handlerForUpdate = new N8nHandler(outbox, db) {
            @Override
            protected Map<String, Object> extractKeys(EventContext ctx) {
                return Map.of("ID", "some-uuid");
            }
            @Override
            protected Map<String, Object> fetchEntityRow(EventContext ctx, Map<String, Object> keys) {
                return new HashMap<>(Map.of("ID", "some-uuid", "title", "Dune", "author_ID", "author-1"));
            }
        };

        when(updateCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "UPDATE", "path", "book-updated", "inputs", List.of("ID", "title", "author_ID"))));
        when(updateCtx.getEvent()).thenReturn("UPDATE");
        when(updateCtx.getCqn()).thenReturn(cqnUpdate);
        when(cqnUpdate.entries()).thenReturn(List.of(Map.of("title", "Dune Messiah")));
        when(updateCtx.get("n8n.prefetch")).thenReturn(new HashMap<>(Map.of("ID", "some-uuid", "title", "Dune", "author_ID", "author-1")));

        handlerForUpdate.beforeMutatingEvent(updateCtx);
        handlerForUpdate.afterCrudEvent(updateCtx);

        ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
        verify(outbox).submit(eq(N8nOutboxHandler.EVENT_TRIGGER), captor.capture());
        @SuppressWarnings("unchecked")
        Map<String, Object> payload = (Map<String, Object>) captor.getValue().getParams().get("payload");
        assertThat(payload)
            .containsEntry("ID", "some-uuid")
            .containsEntry("title", "Dune Messiah")   // updated value
            .containsEntry("author_ID", "author-1");  // unchanged, from prefetch
    }

    @Test
    void afterDelete_nullTarget_doesNotNotify() {
        when(deleteCtx.getTarget()).thenReturn(null);

        handler.beforeMutatingEvent(deleteCtx);
        handler.afterCrudEvent(deleteCtx);

        verify(outbox, never()).submit(any(), any());
    }
}
