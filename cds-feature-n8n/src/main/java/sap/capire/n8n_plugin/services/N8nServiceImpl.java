/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.services;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceDelegator;
import java.util.Map;

/**
 * Default implementation of {@link N8nService}.
 *
 * <p>Extends {@link ServiceDelegator} so that calls to {@link #trigger} are routed through the CAP
 * event bus, allowing {@code @On}/{@code @Before}/{@code @After} handlers to intercept them.
 */
public class N8nServiceImpl extends ServiceDelegator implements N8nService {

  /**
   * @param name the CAP service name used to register this bean
   */
  public N8nServiceImpl(String name) {
    super(name);
  }

  /**
   * Emits a {@code trigger} event on the CAP event bus carrying {@code path} and {@code data}.
   * {@link sap.capire.n8n_plugin.handlers.N8nServiceHandler} listens for this event and forwards it
   * to the outbox.
   */
  @Override
  public void trigger(String path, Map<String, Object> data) {
    // Create a named event so N8nServiceHandler can listen for it with @On(event = "trigger");
    // null as the second argument means this event is not bound to any specific entity type
    EventContext ctx = EventContext.create("trigger", null);
    ctx.put("path", path);
    ctx.put("data", data);
    // emit() dispatches through the CAP event bus, invoking registered @On handlers
    emit(ctx);
  }
}
