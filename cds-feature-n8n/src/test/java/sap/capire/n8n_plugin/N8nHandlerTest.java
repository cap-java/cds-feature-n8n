package sap.capire.n8n_plugin;

import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.EventContext;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.services.request.UserInfo;
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
    EventContext eventCtx;

    @Mock
    CdsEntity entity;

    @Mock
    UserInfo userInfo;

    @Mock
    CqnInsert cqnInsert;

    @InjectMocks
    N8nHandler handler;


    @Test
    void afterCreate_withAnnotation_notifiesWebhook() {
        when(createCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "CREATE", "path", "book-created")));

        // Stub user and CQN entries
        when(createCtx.getEvent()).thenReturn("CREATE");
        when(createCtx.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getName()).thenReturn("testUser");
        when(createCtx.getCqn()).thenReturn(cqnInsert);
        when(cqnInsert.entries()).thenReturn(List.of());

        handler.onCrudEvent(createCtx);

        verify(n8nWebhookService, times(1)).notify(eq("book-created"), argThat(payload ->
            "CREATE".equals(payload.get("event")) &&
            "testUser".equals(payload.get("user"))
        ));
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
    void afterDelete_withAnnotation_notifiesWebhook() {
        when(eventCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "DELETE", "path", "book-deleted")));
        when(eventCtx.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getName()).thenReturn("testUser");
        when(eventCtx.getEvent()).thenReturn("DELETE");

        handler.onCrudEvent(eventCtx);

        verify(n8nWebhookService, times(1)).notify(eq("book-deleted"), argThat(payload ->
            "DELETE".equals(payload.get("event")) &&
            "testUser".equals(payload.get("user"))
        ));
    }

    @Test
    void afterDelete_nullTarget_doesNotNotify() {
        when(eventCtx.getTarget()).thenReturn(null);

        handler.onCrudEvent(eventCtx);

        verify(n8nWebhookService, never()).notify(any(), any());
    }
}
