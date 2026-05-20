package sap.capire.n8n_plugin;

import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsDeleteEventContext;
import com.sap.cds.ql.cqn.CqnInsert;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class N8nHandlerTest {

    @Mock
    N8nWebhookService n8nWebhookService;

    @Mock
    CdsCreateEventContext createCtx;

    @Mock
    CdsDeleteEventContext deleteCtx;

    @Mock
    EventContext eventCtx;

    @Mock
    CdsEntity entity;

    @Mock
    CqnInsert cqnInsert;

    @InjectMocks
    N8nHandler handler;

    @Test
    void onCreate_withAnnotation_notifiesWebhook() {
        when(createCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "CREATE", "path", "book-created")));
        when(createCtx.getEvent()).thenReturn("CREATE");
        when(createCtx.getCqn()).thenReturn(cqnInsert);
        when(cqnInsert.entries()).thenReturn(List.of(Map.of("ID", "1", "title", "Dune")));

        handler.onCrudEvent(createCtx);

        verify(n8nWebhookService).notify(eq("book-created"), argThat(payload ->
            "1".equals(payload.get("ID")) && "Dune".equals(payload.get("title"))
        ));
    }

    @Test
    void onCreate_emptyEntries_doesNotNotify() {
        when(createCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "CREATE", "path", "book-created")));
        when(createCtx.getEvent()).thenReturn("CREATE");
        when(createCtx.getCqn()).thenReturn(cqnInsert);
        when(cqnInsert.entries()).thenReturn(List.of());

        handler.onCrudEvent(createCtx);

        verify(n8nWebhookService, never()).notify(any(), any());
    }

    @Test
    void afterCreate_withoutAnnotation_doesNotNotify() {
        when(createCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of());

        handler.onCrudEvent(createCtx);

        verify(n8nWebhookService, never()).notify(any(), any());
    }

    @Test
    void afterCreate_nullTarget_doesNotNotify() {
        when(createCtx.getTarget()).thenReturn(null);

        handler.onCrudEvent(createCtx);

        verify(n8nWebhookService, never()).notify(any(), any());
    }

    @Test
    void afterCreate_annotationForDifferentEvent_doesNotNotify() {
        when(createCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "DELETE", "path", "book-deleted")));
        when(createCtx.getEvent()).thenReturn("CREATE");

        handler.onCrudEvent(createCtx);

        verify(n8nWebhookService, never()).notify(any(), any());
    }

    @Test
    void onDelete_withAnnotation_notifiesWebhook() {
        N8nHandler handlerForDelete = new N8nHandler(n8nWebhookService) {
            @Override
            protected Map<String, Object> extractDeleteKeys(CdsDeleteEventContext ctx) {
                return Map.of("ID", "some-uuid");
            }
        };

        when(deleteCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "DELETE", "path", "book-deleted")));
        when(deleteCtx.getEvent()).thenReturn("DELETE");

        handlerForDelete.onCrudEvent(deleteCtx);

        verify(n8nWebhookService).notify(eq("book-deleted"), argThat(payload ->
            "some-uuid".equals(payload.get("ID"))
        ));
    }

    @Test
    void afterDelete_nullTarget_doesNotNotify() {
        when(eventCtx.getTarget()).thenReturn(null);

        handler.onCrudEvent(eventCtx);

        verify(n8nWebhookService, never()).notify(any(), any());
    }
}
