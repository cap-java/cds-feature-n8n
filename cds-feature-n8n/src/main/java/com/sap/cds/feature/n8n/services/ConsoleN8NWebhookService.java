/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package com.sap.cds.feature.n8n.services;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;

public class ConsoleN8NWebhookService extends N8nWebhookService {

  private static final Logger log = LoggerFactory.getLogger(ConsoleN8NWebhookService.class);

  private final List<Map<String, Object>> executions =
      Collections.synchronizedList(new ArrayList<>());
  private final AtomicInteger counter = new AtomicInteger(0);

  public ConsoleN8NWebhookService() {
    super("http://console", "", Collections.emptyMap(), null);
  }

  /** Instead of making an HTTP call, logs the path (and payload at DEBUG level). */
  @Override
  public void notify(String path, Map<String, Object> payload, HttpMethod httpMethod) {
    String executionId = "console-exec-" + counter.incrementAndGet();
    String now = Instant.now().toString();
    log.info("[console-n8n-service]: would {} /{}", httpMethod, path);
    log.debug("[console-n8n-service]: payload={}", payload);
    log.debug("[console-n8n-service]: method={}", httpMethod);
    executions.add(
        Map.of(
            "id", executionId, // execution ID, e.g. console-exec-1
            "path", path,
            "method", httpMethod.name(),
            "payload", payload,
            "startedAt", now,
            "finishedAt", now,
            "status", "success"));
  }

  public List<Map<String, Object>> getExecutions() {
    return executions;
  }
}
