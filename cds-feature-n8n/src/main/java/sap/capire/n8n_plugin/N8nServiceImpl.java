package sap.capire.n8n_plugin;

import com.sap.cds.services.EventContext;
import com.sap.cds.services.ServiceDelegator;
import java.util.Map;

public class N8nServiceImpl extends ServiceDelegator implements N8nService {

    public N8nServiceImpl(String name) {
        super(name);
    }

    @Override
    public void trigger(String webhookName, Map<String, Object> data) {
        EventContext ctx = EventContext.create("trigger", null);
        ctx.put("webhookName", webhookName);
        ctx.put("data", data);
        emit(ctx);
    }
}
