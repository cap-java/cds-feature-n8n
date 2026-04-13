package sap.capire.n8n_plugin;

import com.sap.cds.services.runtime.CdsRuntimeConfiguration;
import com.sap.cds.services.runtime.CdsRuntimeConfigurer;

public class N8nHandlerRuntimeConfiguration implements CdsRuntimeConfiguration {

	@Override
	public void eventHandlers(CdsRuntimeConfigurer configurer) {
		// N8nHandler is registered as a Spring bean via N8nAutoConfiguration
	}

}