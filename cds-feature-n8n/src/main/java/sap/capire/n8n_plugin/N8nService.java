package sap.capire.n8n_plugin;

import com.sap.cds.services.Service;
import java.util.Map;

// Exposes programmatic webhook triggering to consuming apps.
// Extending Service registers this as a named CAP service,
// so it participates in CAP's handler chain and can be injected with @Autowired in any event
// handler.
public interface N8nService extends Service {
  String DEFAULT_NAME = "N8nService";

  void trigger(String path, Map<String, Object> data);
}
