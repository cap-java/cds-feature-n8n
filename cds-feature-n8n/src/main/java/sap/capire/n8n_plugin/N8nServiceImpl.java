package sap.capire.n8n_plugin;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceDelegator;
import java.util.Map;

// ServiceDelegator is CAP's base class for custom services; it routes calls through the CAP event
// bus
// so that @On/@Before/@After handlers can intercept them, rather than executing business logic
// directly
public class N8nServiceImpl extends ServiceDelegator implements N8nService {

  public N8nServiceImpl(String name) {
    super(name);
  }

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
