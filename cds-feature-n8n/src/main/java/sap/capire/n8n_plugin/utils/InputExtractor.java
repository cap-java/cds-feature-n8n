/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.utils;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Extracts a named subset of fields from a CDS entity row.
 *
 * <p>Supports both plain string paths and CSN {@code {"=": "..."}} map expressions as produced by
 * the CDS annotation parser. Nested paths (e.g. {@code author.name}) are resolved by walking the
 * map hierarchy.
 */
public class InputExtractor {

  private InputExtractor() {}

  /**
   * Extracts only the fields named in {@code inputs} from {@code row}.
   *
   * <p>When {@code inputs} is empty, all scalar fields are returned — fields whose value is a
   * {@link Map} (to-one association/composition) or a {@link Collection} (to-many) are excluded.
   *
   * @param inputs list of CDS path expressions ({@code String} or {@code {"=": "..."}}) or struct
   *     forms ({@code {path: ..., as: ...}}); empty means "all scalar fields"
   * @param row the full entity row to extract from
   * @return a map containing the requested fields, keyed by the leaf segment or {@code as} alias
   */
  public static Map<String, Object> extract(List<Object> inputs, Map<String, Object> row) {
    // when inputs are empty, send all scalar fields
    if (inputs.isEmpty()) {
      return getAllScalarFieldsByKey(row);
    }
    // else, when inputs are not empty
    Map<String, Object> fieldInputsByKey = new LinkedHashMap<>();
    inputs.forEach(input -> putInput(input, row, fieldInputsByKey));
    return fieldInputsByKey;
  }

  public static String extractPath(Object input) {
    String path = resolvePath(input);
    if (path == null || "$self".equals(path)) return null;
    return stripSelfPrefix(path);
  }

  private static Map<String, Object> getAllScalarFieldsByKey(Map<String, Object> row) {
    Map<String, Object> scalarFieldsByKey = new LinkedHashMap<>();
    row.forEach(
        (key, fieldValue) -> {
          if (!(fieldValue instanceof Map) && !(fieldValue instanceof Collection<?>))
            scalarFieldsByKey.put(key, fieldValue);
        });
    return scalarFieldsByKey;
  }

  private static void putInput(
      Object input, Map<String, Object> row, Map<String, Object> fieldInputsByKey) {
    String path = resolvePath(input);
    if (path != null) {
      if ("$self".equals(path)) {
        // bare $self with no field — expand all scalar fields
        fieldInputsByKey.putAll(getAllScalarFieldsByKey(row));
        return;
      }
      String field = stripSelfPrefix(path);
      fieldInputsByKey.put(leafKey(field), getNestedValue(field, row));
    } else if (input instanceof Map<?, ?> spec && spec.containsKey("path")) {
      String field = stripSelfPrefix(resolvePath(spec.get("path")));
      String key = spec.get("as") instanceof String alias ? alias : leafKey(field);
      fieldInputsByKey.put(key, getNestedValue(field, row));
    }
  }

  /**
   * Unwraps a CDS path expression.
   *
   * @return the path string, or {@code null} if {@code value} is neither a {@code String} nor a
   *     {@code {"=": "..."}} CSN map
   */
  private static String resolvePath(Object value) {
    if (value instanceof String s) {
      return s;
    }
    if (value instanceof Map<?, ?> m && m.containsKey("=")) {
      return (String) m.get("=");
    }
    return null;
  }

  /** Strips the {@code $self.} prefix injected by the CDS annotation compiler. */
  private static String stripSelfPrefix(String path) {
    return path != null && path.startsWith("$self.") ? path.substring(6) : path;
  }

  /**
   * Returns the last segment of a dot-separated path as the default map key (e.g. {@code
   * "items.price"} → {@code "price"}).
   */
  private static String leafKey(String path) {
    int dot = path.lastIndexOf('.');
    return dot >= 0 ? path.substring(dot + 1) : path;
  }

  /**
   * Navigates a dot-separated path through nested maps.
   *
   * @return the value at the path, or {@code null} if any segment is missing
   */
  @SuppressWarnings("unchecked")
  private static Object getNestedValue(String path, Map<String, Object> data) {
    int dot = path.indexOf('.');
    if (dot < 0) {
      return data.get(path);
    }
    Object nested = data.get(path.substring(0, dot));
    return nested instanceof Map
        ? getNestedValue(path.substring(dot + 1), (Map<String, Object>) nested)
        : null;
  }
}
