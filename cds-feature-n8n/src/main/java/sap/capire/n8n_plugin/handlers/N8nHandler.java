/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.handlers;

import com.sap.cds.ql.Select;
import com.sap.cds.ql.cqn.CqnAnalyzer;
import com.sap.cds.reflect.CdsAnnotatable;
import com.sap.cds.reflect.CdsStructuredType;
import com.sap.cds.services.EventContext;
import com.sap.cds.services.cds.ApplicationService;
import com.sap.cds.services.cds.CdsCreateEventContext;
import com.sap.cds.services.cds.CdsDeleteEventContext;
import com.sap.cds.services.cds.CdsReadEventContext;
import com.sap.cds.services.cds.CdsUpdateEventContext;
import com.sap.cds.services.cds.CqnService;
import com.sap.cds.services.handler.EventHandler;
import com.sap.cds.services.handler.annotations.After;
import com.sap.cds.services.handler.annotations.Before;
import com.sap.cds.services.handler.annotations.ServiceName;
import com.sap.cds.services.outbox.OutboxMessage;
import com.sap.cds.services.outbox.OutboxService;
import com.sap.cds.services.persistence.PersistenceService;
import java.util.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import sap.capire.n8n_plugin.utils.ConditionEvaluator;
import sap.capire.n8n_plugin.utils.InputExtractor;

/**
 * CAP event handler that detects {@code @n8n.process.start} annotations and queues webhook calls.
 *
 * <p>Subscribes to all {@link com.sap.cds.services.cds.ApplicationService} instances ({@code
 * value="*"}) so any entity in any service can carry the annotation without the plugin needing to
 * know service names at compile time.
 *
 * <p>For UPDATE and DELETE events, a {@code @Before} handler prefetches the full entity row before
 * the operation and stashes it on the {@link EventContext}. The {@code @After} handler then reads
 * the stash so the webhook payload reflects the pre-delete state or the merged post-update state.
 */
@ServiceName(value = "*", type = ApplicationService.class)
public class N8nHandler implements EventHandler {

  private static final Logger log = LoggerFactory.getLogger(N8nHandler.class);

  // Used in afterAction to skip CRUD events handled by afterCrudEvent, preventing double-firing
  private static final Set<String> CRUD_EVENTS =
      Set.of(
          CqnService.EVENT_CREATE, CqnService.EVENT_READ,
          CqnService.EVENT_UPDATE, CqnService.EVENT_DELETE);

  private static final String ANNOTATION_START = "n8n.process.start";
  // Key used to stash the prefetched row on the EventContext so @After can read it
  private static final String PREFETCH_KEY = "n8n.prefetch";

  private final OutboxService outbox;
  private final PersistenceService db;

  /**
   * @param outbox the persistent outbox service qualified as {@code N8nOutbox}
   * @param db persistence service used to prefetch entity rows before UPDATE/DELETE
   */
  public N8nHandler(OutboxService outbox, PersistenceService db) {
    this.outbox = outbox;
    this.db = db;
  }

  /**
   * Prefetches the full entity row before an UPDATE or DELETE and stashes it on the context. Only
   * runs when the entity has a matching trigger annotation for the current event.
   */
  @Before(event = {CqnService.EVENT_UPDATE, CqnService.EVENT_DELETE})
  public void beforeMutatingEvent(EventContext ctx) {
    CdsStructuredType entity = ctx.getTarget();
    if (entity == null) return;

    if (findTriggers(entity, ctx.getEvent()).isEmpty()) return;

    Map<String, Object> keys = extractKeys(ctx);
    log.info(
        "beforeMutatingEvent for event={} on entity={}: prefetching row for keys={}",
        ctx.getEvent(),
        entity.getQualifiedName(),
        keys.keySet());
    ctx.put(PREFETCH_KEY, fetchEntityRow(ctx, keys));
  }

  /**
   * After-handler for CRUD events. Reads the trigger annotation, builds the payload, and submits it
   * to the outbox. For DELETE, uses the prefetched row; for UPDATE, merges the CQN delta over it so
   * n8n receives the final post-update state.
   */
  @After(
      event = {
        CqnService.EVENT_CREATE,
        CqnService.EVENT_READ,
        CqnService.EVENT_UPDATE,
        CqnService.EVENT_DELETE,
      })
  public void afterCrudEvent(EventContext ctx) {
    CdsStructuredType entity = ctx.getTarget();
    if (entity == null) return;

    String event = ctx.getEvent();
    List<Map<String, Object>> triggers = findTriggers(entity, event);

    log.info(
        "afterCrudEvent for event={} on entity={}: triggers found={}",
        event,
        entity.getQualifiedName(),
        triggers.size());

    triggers.forEach(t -> processTrigger(ctx, t));
  }

  private void processTrigger(EventContext ctx, Map<String, Object> trigger) {
    Object ifExpr = ConditionEvaluator.extractIf(trigger);
    if (ctx instanceof CdsCreateEventContext createCtx) {
      handleCreate(trigger, ifExpr, createCtx);
    } else if (ctx instanceof CdsUpdateEventContext updateCtx) {
      handleUpdate(trigger, ifExpr, updateCtx);
    } else if (ctx instanceof CdsReadEventContext readCtx) {
      handleRead(trigger, ifExpr, readCtx);
    } else if (ctx instanceof CdsDeleteEventContext deleteCtx) {
      handleDelete(trigger, ifExpr, deleteCtx);
    }
  }

  private void handleDelete(
      Map<String, Object> trigger, Object ifExpr, CdsDeleteEventContext deleteCtx) {
    @SuppressWarnings("unchecked")
    Map<String, Object> prefetched = (Map<String, Object>) deleteCtx.get(PREFETCH_KEY);
    if (prefetched != null) submitToOutbox(trigger, ifExpr, prefetched);
  }

  private void handleRead(Map<String, Object> trigger, Object ifExpr, CdsReadEventContext readCtx) {
    for (Map<String, Object> row : readCtx.getResult()) {
      submitToOutbox(trigger, ifExpr, row);
    }
  }

  private void handleUpdate(
      Map<String, Object> trigger, Object ifExpr, CdsUpdateEventContext updateCtx) {
    List<Map<String, Object>> entries = updateCtx.getCqn().entries();
    if (entries.isEmpty()) return;
    // Merge each CQN delta entry (changed fields only) over the prefetched row.
    // Unchanged fields come from the DB prefetch; changed fields from the request.
    @SuppressWarnings("unchecked")
    Map<String, Object> prefetched = (Map<String, Object>) updateCtx.get(PREFETCH_KEY);
    if (prefetched == null) return;
    entries.forEach(
        entry -> {
          Map<String, Object> merged = new HashMap<>(prefetched);
          merged.putAll(entry);
          submitToOutbox(trigger, ifExpr, merged);
        });
  }

  private void handleCreate(
      Map<String, Object> trigger, Object ifExpr, CdsCreateEventContext createCtx) {
    List<Map<String, Object>> entries = createCtx.getCqn().entries();
    if (entries.isEmpty()) return;
    entries.forEach(entry -> submitToOutbox(trigger, ifExpr, entry));
  }

  /**
   * Validates the trigger config, evaluates the {@code if} condition, extracts the payload via
   * {@link InputExtractor}, and submits an outbox message. The actual HTTP call happens in {@link
   * N8nOutboxHandler} after commit.
   */
  private void submitToOutbox(Map<String, Object> trigger, Object ifExpr, Map<String, Object> row) {
    String path = (String) trigger.get("path");
    if (path == null) return;

    if (!ConditionEvaluator.evaluate(ifExpr, row)) {
      log.info("Skipping n8n webhook path={}: if-condition not met", path);
      return;
    }

    @SuppressWarnings("unchecked")
    List<Object> inputs =
        trigger.get("inputs") instanceof List<?> list ? (List<Object>) list : List.of();
    Map<String, Object> payload = InputExtractor.extract(inputs, row);
    log.info("Queuing n8n webhook path={} in outbox with payload keys={}", path, payload.keySet());

    OutboxMessage msg = OutboxMessage.create();
    msg.setParams(Map.of("path", path, "payload", payload));
    outbox.submit(N8nOutboxHandler.EVENT_TRIGGER, msg);
  }

  /** Overridable for tests — avoids mocking the CDS model reflection API. */
  protected CdsAnnotatable resolveUnboundAction(EventContext ctx) {
    String serviceName = ctx.getService().getName();
    String eventName = ctx.getEvent();
    return ctx.getModel()
        .findService(serviceName)
        .flatMap(svc -> svc.actions().filter(a -> a.getName().equals(eventName)).findFirst())
        .orElse(null);
  }

  /**
   * Overridable for tests — avoids the need to mock {@link PersistenceService} and {@link
   * CqnAnalyzer} together. Falls back to keys-only if the row cannot be found.
   */
  protected Map<String, Object> fetchEntityRow(EventContext ctx, Map<String, Object> keys) {
    return db.run(Select.from(ctx.getTarget().getQualifiedName()).matching(keys))
        .first()
        .map(row -> (Map<String, Object>) row)
        .orElse(keys);
  }

  /** Extracts key fields from an UPDATE or DELETE CQN. Overridable for tests. */
  protected Map<String, Object> extractKeys(EventContext ctx) {
    if (ctx instanceof CdsDeleteEventContext deleteCtx) {
      return CqnAnalyzer.create(deleteCtx.getModel()).analyze(deleteCtx.getCqn()).targetKeys();
    }
    if (ctx instanceof CdsUpdateEventContext updateCtx) {
      return CqnAnalyzer.create(updateCtx.getModel()).analyze(updateCtx.getCqn()).targetKeys();
    }
    return Map.of();
  }

  private List<Map<String, Object>> findTriggers(CdsAnnotatable annotatable, String event) {
    List<Map<String, Object>> triggers =
        annotatable.getAnnotationValue(ANNOTATION_START, List.of());
    return triggers.stream().filter(t -> event.equals(t.get("on"))).toList();
  }

  /**
   * After-handler for non-CRUD events (bound and unbound actions). CRUD events are skipped
   * immediately since they are handled by {@link #afterCrudEvent}.
   */
  @After(event = "*")
  public void afterAction(EventContext ctx) {
    if (CRUD_EVENTS.contains(ctx.getEvent())) return;

    log.info("afterAction fired for event: {}", ctx.getEvent());

    CdsAnnotatable annotatable = ctx.getTarget();

    // Unbound actions have no target entity, so we look the action up by name in the service model
    if (annotatable == null) {
      annotatable = resolveUnboundAction(ctx);
    }
    if (annotatable == null) return;

    String on = annotatable.getAnnotationValue(ANNOTATION_START + ".on", (String) null);
    if (!ctx.getEvent().equals(on)) return;

    String path = annotatable.getAnnotationValue(ANNOTATION_START + ".path", (String) null);
    if (path == null) return;

    List<Object> inputs = annotatable.getAnnotationValue(ANNOTATION_START + ".inputs", List.of());

    // Copy into a plain Map so InputExtractor can pull only the annotated fields from it
    Map<String, Object> data = new HashMap<>();
    ctx.keySet().forEach(k -> data.put(k, ctx.get(k)));

    Object ifExpr = annotatable.getAnnotationValue(ANNOTATION_START + ".if", null);
    if (!ConditionEvaluator.evaluate(ifExpr, data)) return;

    Map<String, Object> payload = InputExtractor.extract(inputs, data);
    OutboxMessage msg = OutboxMessage.create();
    msg.setParams(Map.of("path", path, "payload", payload));
    outbox.submit(N8nOutboxHandler.EVENT_TRIGGER, msg);
  }
}
