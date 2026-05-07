package sap.capire.n8n_plugin;

import com.sap.cds.services.Service;
import java.util.Map;

public interface N8nService extends Service {
    String DEFAULT_NAME = "N8nService";

    void trigger(String webhookName, Map<String, Object> data);
}
