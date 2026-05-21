package sap.capire.n8n_plugin;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

class InputExtractor {

  private InputExtractor() {}

  // Pulls only the fields named in "inputs" from the row, with optional aliasing.
  // If inputs is empty, callers should send the full row instead.
  static Map<String, Object> extract(List<Object> inputs, Map<String, Object> row) {
    Map<String, Object> result = new LinkedHashMap<>();
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
    return result;
  }

  // Unwraps a CDS path expression: plain String → returned as-is; {"=": "..."} CSN map → unwrapped.
  private static String resolvePath(Object value) {
    if (value instanceof String s) {
      return s;
    }
    if (value instanceof Map<?, ?> m && m.containsKey("=")) {
      return (String) m.get("=");
    }
    return null;
  }

  private static String stripSelfPrefix(String path) {
    return path != null && path.startsWith("$self.") ? path.substring(6) : path;
  }

  // Returns the last segment of a dot-separated path as the default map key (e.g. "items.price" →
  // "price").
  private static String leafKey(String path) {
    int dot = path.lastIndexOf('.');
    return dot >= 0 ? path.substring(dot + 1) : path;
  }

  // Navigates a dot-separated path through nested maps; returns null if any segment is missing.
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
