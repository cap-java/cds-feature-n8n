/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.services;

import com.sap.cds.services.Service;
import java.util.Map;

/**
 * Programmatic API for triggering n8n webhooks from CAP application code.
 *
 * <p>Extending {@link com.sap.cds.services.Service} registers this as a named CAP service so it
 * participates in the CAP handler chain and can be injected with {@code @Autowired}.
 */
public interface N8nService extends Service {
  String DEFAULT_NAME = "N8nService";

  /**
   * Triggers an n8n webhook at the given path with the supplied payload and HTTP method.
   *
   * <p>The call is enqueued in the CAP persistent outbox and dispatched after the current
   * transaction commits, so a failing webhook never rolls back the business transaction.
   *
   * @param path webhook path appended to {@code n8n.base-url}
   * @param data payload sent as JSON in the request body
   * @param method HTTP method to use (e.g. {@code POST}, {@code PUT}, {@code DELETE}); use {@code
   *     POST} if unsure — it is the n8n webhook default
   */
  void trigger(String path, Map<String, Object> data, String method);
}
