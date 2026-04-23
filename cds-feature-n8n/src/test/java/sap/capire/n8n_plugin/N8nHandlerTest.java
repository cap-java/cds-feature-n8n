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
    void onCreate_withAnnotation_notifiesWebhook() {
        // Stub the annotation to return a trigger matching "CREATE"
        when(createCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "CREATE")));

        // Stub user and CQN entries
        when(createCtx.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getName()).thenReturn("testUser");
        when(createCtx.getCqn()).thenReturn(cqnInsert);
        when(cqnInsert.entries()).thenReturn(List.of());

        handler.onCreate(createCtx);

        // notify should be called once — with "CREATE"
        verify(n8nWebhookService, times(1)).notify(eq("CREATE"), argThat(payload ->
            "CREATE".equals(payload.get("event")) &&
            "testUser".equals(payload.get("user"))
        ));
    }

    @Test
    void onCreate_withoutAnnotation_doesNotNotify() {
        when(createCtx.getTarget()).thenReturn(entity);
        // No annotation entries — findTrigger returns empty Optional
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of());

        handler.onCreate(createCtx);

        verify(n8nWebhookService, never()).notify(any(), any());
    }

    @Test
    void onCreate_nullTarget_doesNotNotify() {
        when(createCtx.getTarget()).thenReturn(null);

        handler.onCreate(createCtx);

        verify(n8nWebhookService, never()).notify(any(), any());
    }

    @Test
    void onCreate_annotationForDifferentEvent_doesNotNotify() {
        when(createCtx.getTarget()).thenReturn(entity);
        // Annotation exists but for DELETE, not CREATE
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "DELETE")));

        handler.onCreate(createCtx);

        verify(n8nWebhookService, never()).notify(any(), any());
    }


    @Test
    void onDelete_withAnnotation_notifiesWebhook() {
        when(eventCtx.getTarget()).thenReturn(entity);
        when(entity.getAnnotationValue("n8n.process.start", List.of()))
            .thenReturn(List.of(Map.of("on", "DELETE")));
        when(eventCtx.getUserInfo()).thenReturn(userInfo);
        when(userInfo.getName()).thenReturn("testUser");

        handler.onDelete(eventCtx);

        verify(n8nWebhookService, times(1)).notify(eq("DELETE"), argThat(payload ->
            "DELETE".equals(payload.get("event")) &&
            "testUser".equals(payload.get("user"))
        ));
    }

    @Test
    void onDelete_nullTarget_doesNotNotify() {
        when(eventCtx.getTarget()).thenReturn(null);

        handler.onDelete(eventCtx);

        verify(n8nWebhookService, never()).notify(any(), any());
    }
}
