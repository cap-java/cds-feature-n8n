/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package com.sap.cds.feature.n8n.handlers;

import com.sap.cds.reflect.CdsModel;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.http.HttpMethod;

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

  private static final Set<HttpMethod> ALLOWED_METHODS =
      Set.of(
          HttpMethod.GET,
          HttpMethod.POST,
          HttpMethod.PUT,
          HttpMethod.PATCH,
          HttpMethod.DELETE,
          HttpMethod.HEAD);

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
                validateEntry(entries.get(i), i, entity.getQualifiedName());
              }
            });
  }

  private void validateEntry(Map<String, Object> entry, int i, String entityName) {
    String prefix = "@n8n.process.start[" + i + "] on entity '" + entityName + "'";
    validateOn(entry.get("on"), prefix);
    validatePath(entry.get("path"), prefix);
    validateMethod(entry.get("method"), prefix);
  }

  private void validateOn(Object value, String prefix) {
    if (value == null) {
      throw new IllegalStateException(
          prefix
              + " is missing required field 'on'."
              + " Specify an event, e.g. { path: 'my-workflow', on: 'CREATE' }.");
    }
  }

  private void validatePath(Object value, String prefix) {
    if (value == null) {
      throw new IllegalStateException(
          prefix
              + " is missing required field 'path'."
              + " Specify a workflow path, e.g. { path: 'my-workflow', on: 'CREATE' }.");
    }
  }

  private void validateMethod(Object value, String prefix) {
    if (value == null) return;
    if (!(value instanceof String v)) {
      throw new IllegalStateException(prefix + " has invalid 'method' value: expected a String.");
    }
    if (!ALLOWED_METHODS.contains(HttpMethod.valueOf(v))) {
      String allowedMethods =
          ALLOWED_METHODS.stream().map(HttpMethod::name).collect(Collectors.joining(", "));
      throw new IllegalStateException(
          prefix
              + " has invalid 'method' value '"
              + v
              + "'. Allowed values: "
              + allowedMethods
              + ".");
    }
  }
}
