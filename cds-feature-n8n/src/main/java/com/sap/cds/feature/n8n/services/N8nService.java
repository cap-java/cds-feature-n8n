/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package com.sap.cds.feature.n8n.services;

import com.sap.cds.services.Service;
import java.util.Map;
import org.springframework.http.HttpMethod;

/**
 * Programmatic API for triggering n8n webhooks from CAP application code.
 *
 * <p>Extending {@link com.sap.cds.services.Service} registers this as a named CAP service so it
 * participates in the CAP handler chain and can be injected with {@code @Autowired}.
 */
public interface N8nService extends Service {
  String DEFAULT_NAME = "N8nService";

  /**
   * Triggers an n8n webhook at the given path with the supplied payload, using {@code POST}.
   *
   * <p>Convenience overload of {@link #trigger(String, Map, HttpMethod)} that defaults the HTTP
   * method to {@code POST} — the n8n webhook default.
   *
   * @param path webhook path appended to {@code n8n.base-url}
   * @param payload payload sent as JSON in the request body
   */
  default void trigger(String path, Map<String, Object> payload) {
    trigger(path, payload, HttpMethod.POST);
  }

  /**
   * Triggers an n8n webhook at the given path with the supplied payload and HTTP method.
   *
   * <p>The call is enqueued in the CAP persistent outbox and dispatched after the current
   * transaction commits, so a failing webhook never rolls back the business transaction.
   *
   * @param path webhook path appended to {@code n8n.base-url}
   * @param payload payload sent as JSON in the request body
   * @param method HTTP method to use (e.g. {@code HttpMethod.POST}, {@code HttpMethod.PUT})
   */
  void trigger(String path, Map<String, Object> payload, HttpMethod method);
}
