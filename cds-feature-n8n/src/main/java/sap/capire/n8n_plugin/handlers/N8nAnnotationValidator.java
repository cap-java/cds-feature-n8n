/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.handlers;

import com.sap.cds.reflect.CdsModel;
import java.util.List;
import java.util.Map;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;

/**
 * Validates {@code @n8n.process.start} annotations at startup. Throws {@link IllegalStateException}
 * if any array entry is missing the required {@code on} or {@code path} field so misconfigurations
 * are caught early rather than silently ignored at runtime.
 */
public class N8nAnnotationValidator {

  private final CdsModel cdsModel;

  public N8nAnnotationValidator(CdsModel cdsModel) {
    this.cdsModel = cdsModel;
  }

  @EventListener
  public void validateN8nAnnotations(ApplicationReadyEvent ignored) {
    cdsModel
        .entities()
        .forEach(
            entity -> {
              Object raw = entity.getAnnotationValue(N8nHandler.ANNOTATION_START, List.of());
              if (!(raw instanceof List<?> rawList)) return;
              @SuppressWarnings("unchecked")
              List<Map<String, Object>> entries = (List<Map<String, Object>>) rawList;
              for (int i = 0; i < entries.size(); i++) {
                Map<String, Object> entry = entries.get(i);
                if (entry.get("on") == null) {
                  throw new IllegalStateException(
                      "@n8n.process.start["
                          + i
                          + "] on entity '"
                          + entity.getQualifiedName()
                          + "' is missing required field 'on'."
                          + " Specify an event, e.g."
                          + " { path: 'my-workflow', on: 'CREATE' }.");
                }
                if (entry.get("path") == null) {
                  throw new IllegalStateException(
                      "@n8n.process.start["
                          + i
                          + "] on entity '"
                          + entity.getQualifiedName()
                          + "' is missing required field 'path'."
                          + " Specify a workflow path, e.g."
                          + " { path: 'my-workflow', on: 'CREATE' }.");
                }
              }
            });
  }
}
