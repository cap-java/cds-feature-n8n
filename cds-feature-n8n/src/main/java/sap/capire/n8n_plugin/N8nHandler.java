/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin;

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
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

// value="*" subscribes to every ApplicationService so any entity in any service can carry the
// annotation without the plugin needing to know service names at compile time
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

  public N8nHandler(OutboxService outbox, PersistenceService db) {
    this.outbox = outbox;
    this.db = db;
  }

  // For UPDATE and DELETE the CQN only carries key fields (DELETE) or changed fields (UPDATE),
  // so we fetch the full row from the DB before the operation and stash it on the context.
  // @After then uses the stash: DELETE sends it as-is, UPDATE merges the CQN delta over it
  // so n8n receives the final post-update state.
  @Before(event = {CqnService.EVENT_UPDATE, CqnService.EVENT_DELETE})
  public void beforeMutatingEvent(EventContext ctx) {
    CdsStructuredType entity = ctx.getTarget();
    if (entity == null) return;

    if (findTrigger(entity, ctx.getEvent()).isEmpty()) return;

    Map<String, Object> keys = extractKeys(ctx);
    log.info(
        "beforeMutatingEvent for event={} on entity={}: prefetching row for keys={}",
        ctx.getEvent(),
        entity.getQualifiedName(),
        keys.keySet());
    ctx.put(PREFETCH_KEY, fetchEntityRow(ctx, keys));
  }

  // Annotation-based path: entities annotated with @n8n.process.start trigger n8n webhooks
  // without requiring application code — the annotation alone is enough to wire up the integration.
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
    var trigger = findTrigger(entity, event);
    log.info(
        "afterCrudEvent for event={} on entity={}: trigger found={}",
        event,
        entity.getQualifiedName(),
        trigger.isPresent());
    trigger.ifPresent(
        t -> {
          Map<String, Object> row;
          if (ctx instanceof CdsCreateEventContext createCtx) {
            List<Map<String, Object>> entries = createCtx.getCqn().entries();
            if (entries.isEmpty()) return;
            entries.forEach(entry -> submitToOutbox(t, entry));
            return;
          } else if (ctx instanceof CdsUpdateEventContext updateCtx) {
            List<Map<String, Object>> entries = updateCtx.getCqn().entries();
            if (entries.isEmpty()) return;
            // Merge each CQN delta entry (changed fields only) over the prefetched row.
            // Unchanged fields come from the DB prefetch; changed fields from the request.
            @SuppressWarnings("unchecked")
            Map<String, Object> prefetched = (Map<String, Object>) ctx.get(PREFETCH_KEY);
            if (prefetched == null) return;
            entries.forEach(
                entry -> {
                  Map<String, Object> merged = new HashMap<>(prefetched);
                  merged.putAll(entry);
                  submitToOutbox(t, merged);
                });
            return;
          } else if (ctx instanceof CdsReadEventContext readCtx) {
            // CAP populates the result before @After handlers run, so it is safe to read here
            var first = readCtx.getResult().stream().findFirst();
            if (first.isEmpty()) return;
            row = first.get();
          } else if (ctx instanceof CdsDeleteEventContext) {
            // Row was prefetched before deletion; use it directly
            @SuppressWarnings("unchecked")
            Map<String, Object> prefetched = (Map<String, Object>) ctx.get(PREFETCH_KEY);
            if (prefetched == null) return;
            row = prefetched;
          } else {
            return;
          }
          submitToOutbox(t, row);
        });
  }

  // Validates inputs, builds the outbox message, and submits it transactionally.
  // The actual HTTP call happens in N8nOutboxHandler after the transaction commits.
  private void submitToOutbox(Map<String, Object> trigger, Map<String, Object> row) {
    String path = (String) trigger.get("path");
    if (path == null) return;

    @SuppressWarnings("unchecked")
    List<Object> inputs =
        trigger.get("inputs") instanceof List<?> list ? (List<Object>) list : List.of();
    if (inputs.isEmpty()) {
      log.warn(
          "Skipping n8n notification for path={}: @n8n.process.start.inputs is required but not specified",
          path);
      return;
    }
    Map<String, Object> payload = InputExtractor.extract(inputs, row);
    log.info("Queuing n8n webhook path={} in outbox with payload keys={}", path, payload.keySet());

    OutboxMessage msg = OutboxMessage.create();
    msg.setParams(Map.of("path", path, "payload", payload));
    outbox.submit(N8nOutboxHandler.EVENT_TRIGGER, msg);
  }

  // Overridable for tests — avoids mocking the CDS model reflection API.
  protected CdsAnnotatable resolveUnboundAction(EventContext ctx) {
    String serviceName = ctx.getService().getName();
    String eventName = ctx.getEvent();
    return ctx.getModel()
        .findService(serviceName)
        .flatMap(svc -> svc.actions().filter(a -> a.getName().equals(eventName)).findFirst())
        .orElse(null);
  }

  // Overridable for tests — avoids the need to mock PersistenceService and CqnAnalyzer together.
  // Falls back to keys-only if the row cannot be found (e.g. already deleted by a concurrent tx).
  protected Map<String, Object> fetchEntityRow(EventContext ctx, Map<String, Object> keys) {
    return db.run(Select.from(ctx.getTarget().getQualifiedName()).matching(keys))
        .first()
        .map(r -> (Map<String, Object>) r)
        .orElse(keys);
  }

  // Extracts the key fields from an UPDATE or DELETE CQN. Overridable for tests.
  protected Map<String, Object> extractKeys(EventContext ctx) {
    if (ctx instanceof CdsDeleteEventContext deleteCtx) {
      return CqnAnalyzer.create(deleteCtx.getModel()).analyze(deleteCtx.getCqn()).targetKeys();
    }
    if (ctx instanceof CdsUpdateEventContext updateCtx) {
      return CqnAnalyzer.create(updateCtx.getModel()).analyze(updateCtx.getCqn()).targetKeys();
    }
    return Map.of();
  }

  // The annotation value is a list of trigger configs (e.g. [{on: 'CREATE'}, {on: 'DELETE'}]).
  // Returns the matching trigger config, used to read "path" and "inputs", or empty if the
  // annotation has no entry for this event.
  private java.util.Optional<Map<String, Object>> findTrigger(
      CdsAnnotatable annotatable, String event) {
    List<Map<String, Object>> triggers =
        annotatable.getAnnotationValue(ANNOTATION_START, List.of());
    return triggers.stream()
        .filter(t -> event.equals(t.get("on")))
        .findFirst(); // if the same event appears twice in the annotation, fire only once
  }

  // CRUD events are filtered out immediately because they are handled by afterCrudEvent above
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

    // Copy into a plain Map so InputExtractor can pull only the annotated fields from it
    Map<String, Object> data = new HashMap<>();
    ctx.keySet().forEach(k -> data.put(k, ctx.get(k)));

    List<Object> inputs = annotatable.getAnnotationValue(ANNOTATION_START + ".inputs", List.of());
    if (inputs.isEmpty()) {
      log.warn(
          "Skipping n8n notification for path={}: @n8n.process.start.inputs is required but not specified",
          path);
      return;
    }
    Map<String, Object> payload = InputExtractor.extract(inputs, data);
    OutboxMessage msg = OutboxMessage.create();
    msg.setParams(Map.of("path", path, "payload", payload));
    outbox.submit(N8nOutboxHandler.EVENT_TRIGGER, msg);
  }
}
