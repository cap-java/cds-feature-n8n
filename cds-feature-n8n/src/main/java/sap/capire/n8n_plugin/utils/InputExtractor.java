/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.utils;

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
   * <p>When {@code inputs} is empty, all scalar fields (those whose value is not a {@link Map}) are
   * returned — associations and compositions expand as nested maps and are excluded.
   *
   * @param inputs list of CDS path expressions ({@code String} or {@code {"=": "..."}}) or struct
   *     forms ({@code {path: ..., as: ...}}); empty means "all scalar fields"
   * @param row the full entity row to extract from
   * @return a map containing the requested fields, keyed by the leaf segment or {@code as} alias
   */
  public static Map<String, Object> extract(List<Object> inputs, Map<String, Object> row) {
    Map<String, Object> result = new LinkedHashMap<>();
    if (inputs.isEmpty()) {
      row.forEach(
          (key, value) -> {
            if (!(value instanceof Map)) result.put(key, value);
          });
    } else {
      for (Object input : inputs) {
        // try to read the input as a plain path expression (String or {"=": "..."} CSN map)
        String path = resolvePath(input);
        if (path != null) {
          // bare path: strip $self., then use the last segment as the output key
          String field = stripSelfPrefix(path);
          result.put(leafKey(field), getNestedValue(field, row));
        } else if (input instanceof Map<?, ?> spec && spec.containsKey("path")) {
          // struct form {path: ..., as: ...}: resolve the path, use "as" as the key if present
          String field = stripSelfPrefix(resolvePath(spec.get("path")));
          String key = spec.get("as") instanceof String alias ? alias : leafKey(field);
          result.put(key, getNestedValue(field, row));
        }
      }
    }
    return result;
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
