/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.handlers;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import com.sap.cds.Result;
import com.sap.cds.ql.cqn.CqnInsert;
import com.sap.cds.ql.cqn.CqnUpdate;
import com.sap.cds.reflect.CdsAnnotatable;
import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsDeleteEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.outbox.OutboxMessage;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class N8nHandlerTest {

  @Mock OutboxService outbox;

  @Mock PersistenceService db;

  @Mock CdsCreateEventContext createCtx;

  @Mock CdsDeleteEventContext deleteCtx;

  @Mock CdsUpdateEventContext updateCtx;

  @Mock CdsReadEventContext readCtx;

  @Mock Result readResult;

  @Mock EventContext eventCtx;

  @Mock CdsEntity entity;

  @Mock CqnInsert cqnInsert;

  @Mock CqnUpdate cqnUpdate;

  @InjectMocks N8nHandler handler;

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
        .thenReturn(
            List.of(
                Map.of("on", "CREATE", "path", "book-created", "inputs", List.of("ID", "title"))));
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
        .thenReturn(
            List.of(
                Map.of("on", "CREATE", "path", "book-created", "inputs", List.of("ID", "title"))));
    when(createCtx.getEvent()).thenReturn("CREATE");
    when(createCtx.getCqn()).thenReturn(cqnInsert);
    when(cqnInsert.entries())
        .thenReturn(
            List.of(
                Map.of("ID", "1", "title", "Dune"),
                Map.of("ID", "2", "title", "Dune Messiah"),
                Map.of("ID", "3", "title", "Children of Dune")));

    handler.afterCrudEvent(createCtx);

    List<Map<String, Object>> payloads = captureAllPayloads();
    assertThat(payloads)
        .hasSize(3)
        .anyMatch(p -> "1".equals(p.get("ID")))
        .anyMatch(p -> "2".equals(p.get("ID")))
        .anyMatch(p -> "3".equals(p.get("ID")));
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
    when(entity.getAnnotationValue("n8n.process.start", List.of())).thenReturn(List.of());

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
    N8nHandler handlerForDelete =
        new N8nHandler(outbox, db) {
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
        .thenReturn(
            List.of(
                Map.of(
                    "on",
                    "DELETE",
                    "path",
                    "book-deleted",
                    "inputs",
                    List.of("ID", "title", "author_ID"))));
    when(deleteCtx.getEvent()).thenReturn("DELETE");
    when(deleteCtx.get("n8n.prefetch"))
        .thenReturn(Map.of("ID", "some-uuid", "title", "Dune", "author_ID", "author-1"));

    handlerForDelete.beforeMutatingEvent(deleteCtx);
    handlerForDelete.afterCrudEvent(deleteCtx);

    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).submit(eq(N8nOutboxHandler.EVENT_TRIGGER), captor.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> payload =
        (Map<String, Object>) captor.getValue().getParams().get("payload");
    assertThat(payload)
        .containsEntry("ID", "some-uuid")
        .containsEntry("title", "Dune")
        .containsEntry("author_ID", "author-1");
  }

  // PATCH only sends changed fields; n8n should still receive the full post-update row
  @Test
  void onUpdate_withAnnotation_mergesDeltaOverPrefetchedRow() {
    N8nHandler handlerForUpdate =
        new N8nHandler(outbox, db) {
          @Override
          protected Map<String, Object> extractKeys(EventContext ctx) {
            return Map.of("ID", "some-uuid");
          }

          @Override
          protected Map<String, Object> fetchEntityRow(EventContext ctx, Map<String, Object> keys) {
            return new HashMap<>(
                Map.of("ID", "some-uuid", "title", "Dune", "author_ID", "author-1"));
          }
        };

    when(updateCtx.getTarget()).thenReturn(entity);
    when(entity.getAnnotationValue("n8n.process.start", List.of()))
        .thenReturn(
            List.of(
                Map.of(
                    "on",
                    "UPDATE",
                    "path",
                    "book-updated",
                    "inputs",
                    List.of("ID", "title", "author_ID"))));
    when(updateCtx.getEvent()).thenReturn("UPDATE");
    when(updateCtx.getCqn()).thenReturn(cqnUpdate);
    when(cqnUpdate.entries()).thenReturn(List.of(Map.of("title", "Dune Messiah")));
    when(updateCtx.get("n8n.prefetch"))
        .thenReturn(
            new HashMap<>(Map.of("ID", "some-uuid", "title", "Dune", "author_ID", "author-1")));

    handlerForUpdate.beforeMutatingEvent(updateCtx);
    handlerForUpdate.afterCrudEvent(updateCtx);

    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).submit(eq(N8nOutboxHandler.EVENT_TRIGGER), captor.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> payload =
        (Map<String, Object>) captor.getValue().getParams().get("payload");
    assertThat(payload)
        .containsEntry("ID", "some-uuid")
        .containsEntry("title", "Dune Messiah") // updated value
        .containsEntry("author_ID", "author-1"); // unchanged, from prefetch
  }

  @Test
  void onUpdate_bulkUpdate_notifiesOncePerEntry() {
    N8nHandler handlerForUpdate =
        new N8nHandler(outbox, db) {
          @Override
          protected Map<String, Object> extractKeys(EventContext ctx) {
            return Map.of("ID", "some-uuid");
          }

          @Override
          protected Map<String, Object> fetchEntityRow(EventContext ctx, Map<String, Object> keys) {
            return new HashMap<>(
                Map.of("ID", "some-uuid", "title", "Dune", "author_ID", "author-1"));
          }
        };

    when(updateCtx.getTarget()).thenReturn(entity);
    when(entity.getAnnotationValue("n8n.process.start", List.of()))
        .thenReturn(
            List.of(
                Map.of("on", "UPDATE", "path", "book-updated", "inputs", List.of("ID", "title"))));
    when(updateCtx.getEvent()).thenReturn("UPDATE");
    when(updateCtx.getCqn()).thenReturn(cqnUpdate);
    when(cqnUpdate.entries())
        .thenReturn(
            List.of(
                Map.of("title", "Dune Messiah"),
                Map.of("title", "Children of Dune"),
                Map.of("title", "God Emperor of Dune")));
    when(updateCtx.get("n8n.prefetch"))
        .thenReturn(
            new HashMap<>(Map.of("ID", "some-uuid", "title", "Dune", "author_ID", "author-1")));

    handlerForUpdate.beforeMutatingEvent(updateCtx);
    handlerForUpdate.afterCrudEvent(updateCtx);

    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox, times(3)).submit(eq(N8nOutboxHandler.EVENT_TRIGGER), captor.capture());
    @SuppressWarnings("unchecked")
    List<String> titles =
        captor.getAllValues().stream()
            .map(m -> (Map<String, Object>) m.getParams().get("payload"))
            .map(p -> (String) p.get("title"))
            .toList();
    assertThat(titles)
        .containsExactlyInAnyOrder("Dune Messiah", "Children of Dune", "God Emperor of Dune");
  }

  @Test
  void afterDelete_nullTarget_doesNotNotify() {
    when(deleteCtx.getTarget()).thenReturn(null);

    handler.beforeMutatingEvent(deleteCtx);
    handler.afterCrudEvent(deleteCtx);

    verify(outbox, never()).submit(any(), any());
  }

  // --- READ event ---

  @Test
  void onRead_withAnnotation_notifiesWebhookWithFirstResult() {
    when(readCtx.getTarget()).thenReturn(entity);
    when(entity.getAnnotationValue("n8n.process.start", List.of()))
        .thenReturn(
            List.of(Map.of("on", "READ", "path", "book-read", "inputs", List.of("ID", "title"))));
    when(readCtx.getEvent()).thenReturn("READ");
    when(readCtx.getResult()).thenReturn(readResult);
    when(readResult.stream()).thenAnswer(inv -> Stream.of(Map.of("ID", "1", "title", "Dune")));

    handler.afterCrudEvent(readCtx);

    Map<String, Object> payload = capturePayload();
    assertThat(payload).containsEntry("ID", "1").containsEntry("title", "Dune");
  }

  @Test
  void onRead_emptyResult_doesNotNotify() {
    when(readCtx.getTarget()).thenReturn(entity);
    when(entity.getAnnotationValue("n8n.process.start", List.of()))
        .thenReturn(List.of(Map.of("on", "READ", "path", "book-read", "inputs", List.of("ID"))));
    when(readCtx.getEvent()).thenReturn("READ");
    when(readCtx.getResult()).thenReturn(readResult);
    when(readResult.stream()).thenAnswer(inv -> Stream.empty());

    handler.afterCrudEvent(readCtx);

    verify(outbox, never()).submit(any(), any());
  }

  // --- afterAction: bound actions (target entity carries the annotation) ---

  @Test
  void onBoundAction_withAnnotation_notifiesWebhook() {
    when(eventCtx.getEvent()).thenReturn("confirmOrder");
    when(eventCtx.getTarget()).thenReturn(entity);
    when(entity.getAnnotationValue("n8n.process.start.on", (String) null))
        .thenReturn("confirmOrder");
    when(entity.getAnnotationValue("n8n.process.start.path", (String) null))
        .thenReturn("order-confirmed");
    when(entity.getAnnotationValue("n8n.process.start.inputs", List.of()))
        .thenReturn(List.of("orderID"));
    when(eventCtx.keySet()).thenReturn(java.util.Set.of("orderID"));
    when(eventCtx.get("orderID")).thenReturn("order-42");

    handler.afterAction(eventCtx);

    Map<String, Object> payload = capturePayload();
    assertThat(payload).containsEntry("orderID", "order-42");
  }

  @Test
  void onBoundAction_annotationForDifferentEvent_doesNotNotify() {
    when(eventCtx.getEvent()).thenReturn("confirmOrder");
    when(eventCtx.getTarget()).thenReturn(entity);
    when(entity.getAnnotationValue("n8n.process.start.on", (String) null))
        .thenReturn("cancelOrder");

    handler.afterAction(eventCtx);

    verify(outbox, never()).submit(any(), any());
  }

  @Test
  void onBoundAction_noInputs_sendsAllScalarFields() {
    when(eventCtx.getEvent()).thenReturn("confirmOrder");
    when(eventCtx.getTarget()).thenReturn(entity);
    when(entity.getAnnotationValue("n8n.process.start.on", (String) null))
        .thenReturn("confirmOrder");
    when(entity.getAnnotationValue("n8n.process.start.path", (String) null))
        .thenReturn("order-confirmed");
    when(entity.getAnnotationValue("n8n.process.start.inputs", List.of())).thenReturn(List.of());
    when(eventCtx.keySet()).thenReturn(java.util.Set.of("orderId", "total"));
    when(eventCtx.get("orderId")).thenReturn("ord-1");
    when(eventCtx.get("total")).thenReturn(99);

    handler.afterAction(eventCtx);

    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).submit(eq(N8nOutboxHandler.EVENT_TRIGGER), captor.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> payload =
        (Map<String, Object>) captor.getValue().getParams().get("payload");
    assertThat(payload).containsEntry("orderId", "ord-1").containsEntry("total", 99);
  }

  @Test
  void onBoundAction_missingPath_doesNotNotify() {
    when(eventCtx.getEvent()).thenReturn("confirmOrder");
    when(eventCtx.getTarget()).thenReturn(entity);
    when(entity.getAnnotationValue("n8n.process.start.on", (String) null))
        .thenReturn("confirmOrder");
    when(entity.getAnnotationValue("n8n.process.start.path", (String) null)).thenReturn(null);

    handler.afterAction(eventCtx);

    verify(outbox, never()).submit(any(), any());
  }

  // --- afterAction: unbound actions (no target, resolved via model) ---

  @Test
  void onUnboundAction_withAnnotation_notifiesWebhook() {
    CdsAnnotatable actionAnnotatable = mock(CdsAnnotatable.class);
    N8nHandler handlerForUnbound =
        new N8nHandler(outbox, db) {
          @Override
          protected CdsAnnotatable resolveUnboundAction(EventContext ctx) {
            return actionAnnotatable;
          }
        };

    when(eventCtx.getEvent()).thenReturn("globalAction");
    when(eventCtx.getTarget()).thenReturn(null);
    when(actionAnnotatable.getAnnotationValue("n8n.process.start.on", (String) null))
        .thenReturn("globalAction");
    when(actionAnnotatable.getAnnotationValue("n8n.process.start.path", (String) null))
        .thenReturn("global-action-fired");
    when(actionAnnotatable.getAnnotationValue("n8n.process.start.inputs", List.of()))
        .thenReturn(List.of("param1"));
    when(eventCtx.keySet()).thenReturn(java.util.Set.of("param1"));
    when(eventCtx.get("param1")).thenReturn("value1");

    handlerForUnbound.afterAction(eventCtx);

    ArgumentCaptor<OutboxMessage> captor = ArgumentCaptor.forClass(OutboxMessage.class);
    verify(outbox).submit(eq(N8nOutboxHandler.EVENT_TRIGGER), captor.capture());
    @SuppressWarnings("unchecked")
    Map<String, Object> payload =
        (Map<String, Object>) captor.getValue().getParams().get("payload");
    assertThat(payload).containsEntry("param1", "value1");
  }

  @Test
  void onUnboundAction_notFoundInModel_doesNotNotify() {
    N8nHandler handlerForUnbound =
        new N8nHandler(outbox, db) {
          @Override
          protected CdsAnnotatable resolveUnboundAction(EventContext ctx) {
            return null;
          }
        };

    when(eventCtx.getEvent()).thenReturn("unknownAction");
    when(eventCtx.getTarget()).thenReturn(null);

    handlerForUnbound.afterAction(eventCtx);

    verify(outbox, never()).submit(any(), any());
  }

  @Test
  void onCreate_withIfCondition_conditionMet_notifies() {
    Map<String, Object> ifExpr =
        Map.of("xpr", List.of(Map.of("ref", List.of("status")), "=", Map.of("val", "shipped")));
    when(createCtx.getTarget()).thenReturn(entity);
    when(entity.getAnnotationValue("n8n.process.start", List.of()))
        .thenReturn(
            List.of(
                Map.of(
                    "on",
                    "CREATE",
                    "path",
                    "item-shipped",
                    "inputs",
                    List.of("ID", "status"),
                    "if",
                    ifExpr)));
    when(createCtx.getEvent()).thenReturn("CREATE");
    when(createCtx.getCqn()).thenReturn(cqnInsert);
    when(cqnInsert.entries()).thenReturn(List.of(Map.of("ID", "1", "status", "shipped")));

    handler.afterCrudEvent(createCtx);

    Map<String, Object> payload = capturePayload();
    assertThat(payload).containsEntry("ID", "1").containsEntry("status", "shipped");
  }

  @Test
  void onCreate_withIfCondition_conditionNotMet_doesNotNotify() {
    Map<String, Object> ifExpr =
        Map.of("xpr", List.of(Map.of("ref", List.of("status")), "=", Map.of("val", "shipped")));
    when(createCtx.getTarget()).thenReturn(entity);
    when(entity.getAnnotationValue("n8n.process.start", List.of()))
        .thenReturn(
            List.of(
                Map.of(
                    "on",
                    "CREATE",
                    "path",
                    "item-shipped",
                    "inputs",
                    List.of("ID", "status"),
                    "if",
                    ifExpr)));
    when(createCtx.getEvent()).thenReturn("CREATE");
    when(createCtx.getCqn()).thenReturn(cqnInsert);
    when(cqnInsert.entries()).thenReturn(List.of(Map.of("ID", "1", "status", "pending")));

    handler.afterCrudEvent(createCtx);

    verify(outbox, never()).submit(any(), any());
  }

  @Test
  void onBoundAction_withIfCondition_conditionNotMet_doesNotNotify() {
    Map<String, Object> ifExpr =
        Map.of("xpr", List.of(Map.of("ref", List.of("status")), "=", Map.of("val", "shipped")));
    when(eventCtx.getEvent()).thenReturn("confirmOrder");
    when(eventCtx.getTarget()).thenReturn(entity);
    when(entity.getAnnotationValue("n8n.process.start.on", (String) null))
        .thenReturn("confirmOrder");
    when(entity.getAnnotationValue("n8n.process.start.path", (String) null))
        .thenReturn("order-confirmed");
    when(entity.getAnnotationValue("n8n.process.start.inputs", List.of())).thenReturn(List.of());
    when(entity.getAnnotationValue("n8n.process.start.if", null)).thenReturn(ifExpr);
    when(eventCtx.keySet()).thenReturn(java.util.Set.of("status"));
    when(eventCtx.get("status")).thenReturn("pending");

    handler.afterAction(eventCtx);

    verify(outbox, never()).submit(any(), any());
  }

  @Test
  void afterAction_crudEvent_isIgnored() {
    when(eventCtx.getEvent()).thenReturn("CREATE");

    handler.afterAction(eventCtx);

    verify(outbox, never()).submit(any(), any());
  }

  @Test
  void beforeMutatingEvent_noAnnotation_doesNotPrefetch() {
    when(deleteCtx.getTarget()).thenReturn(entity);
    when(entity.getAnnotationValue("n8n.process.start", List.of())).thenReturn(List.of());
    when(deleteCtx.getEvent()).thenReturn("DELETE");
    handler.beforeMutatingEvent(deleteCtx);
    verify(deleteCtx, never()).put(any(), any());
  }

  @Test
  void fetchEntityRow_rowNotFound_fallsBackToKeys_viaBeforeMutatingEvent() {
    Map<String, Object> keys = Map.of("ID", "42");
    N8nHandler h =
        new N8nHandler(outbox, db) {
          @Override
          protected Map<String, Object> extractKeys(EventContext ctx) {
            return keys;
          }

          @Override
          protected Map<String, Object> fetchEntityRow(EventContext ctx, Map<String, Object> k) {
            // call the real implementation
            return super.fetchEntityRow(ctx, k);
          }
        };

    when(deleteCtx.getTarget()).thenReturn(entity);
    when(entity.getAnnotationValue("n8n.process.start", List.of()))
        .thenReturn(
            List.of(Map.of("on", "DELETE", "path", "book-deleted", "inputs", List.of("ID"))));
    when(deleteCtx.getEvent()).thenReturn("DELETE");
    when(entity.getQualifiedName()).thenReturn("my.Entity");

    Result result = mock(Result.class);
    when(db.run(any(com.sap.cds.ql.cqn.CqnSelect.class))).thenReturn(result);
    when(result.first()).thenReturn(Optional.empty());

    // When row not found, fetchEntityRow falls back to keys — prefetch stash equals keys
    h.beforeMutatingEvent(deleteCtx);
    verify(deleteCtx).put("n8n.prefetch", keys);
  }

  @Test
  void afterAction_resolveUnboundAction_serviceNotFound_doesNotNotify() {
    com.sap.cds.reflect.CdsModel model = mock(com.sap.cds.reflect.CdsModel.class);
    com.sap.cds.services.Service service = mock(com.sap.cds.services.Service.class);

    when(eventCtx.getEvent()).thenReturn("myAction");
    when(eventCtx.getTarget()).thenReturn(null);
    when(eventCtx.getModel()).thenReturn(model);
    when(eventCtx.getService()).thenReturn(service);
    when(service.getName()).thenReturn("MyService");
    when(model.findService("MyService")).thenReturn(Optional.empty());

    handler.afterAction(eventCtx);

    verify(outbox, never()).submit(any(), any());
  }
}
