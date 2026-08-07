/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.services;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ConsoleN8NWebhookService extends N8nWebhookService {

  private static final Logger log = LoggerFactory.getLogger(ConsoleN8NWebhookService.class);

  private final List<Map<String, Object>> executions = new ArrayList<>();
  private int counter = 0;

  public ConsoleN8NWebhookService() {
    super("http://console", "", null);
  }

  /**
   * Instead of making an HTTP Call, it just logs the path and the payload to simulate the fake call
   */
  @Override
  public void notify(String path, Map<String, Object> payload) {
    String executionId = "console-exec-" + (++counter);
    log.info("[console-n8n-service]: would POST /webhook/{} - payload: {}", path, payload);
    executions.add(
        Map.of("executionId", executionId, "path", path, "payload", payload, "status", "success"));
  }

  public List<Map<String, Object>> getExecutions() {
    return executions;
  }
}
