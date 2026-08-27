/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package com.sap.cds.feature.n8n.utils;

import com.sap.cds.ql.CQL;
import com.sap.cds.ql.Selectable;
import com.sap.cds.reflect.CdsStructuredType;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Extracts a named subset of fields from a CDS entity row.
 *
 * <p>Supports both plain string paths and CSN {@code {"=": "..."}} map expressions as produced by
 * the CDS annotation parser. Nested paths (e.g. {@code author.name}) are resolved by walking the
 * map hierarchy.
 */
public class InputExtractor {

  private static final Logger log = LoggerFactory.getLogger(InputExtractor.class);
  private static final String BARE_SELF = "$self";

  private InputExtractor() {}

  /**
   * Extracts only the fields named in {@code inputs} from {@code row}.
   *
   * <p>When {@code inputs} is empty, all direct fields are returned — fields whose value is a
   * {@link Map} (to-one association/composition) or a {@link Collection} (to-many) are excluded.
   *
   * @param inputs list of CDS path expressions ({@code String} or {@code {"=": "..."}}) or struct
   *     forms ({@code {path: ..., as: ...}}); empty means "all direct fields"
   * @param row the full entity row to extract from
   * @return a map containing the requested fields, keyed by the leaf segment or {@code as} alias
   */
  public static Map<String, Object> extract(List<Object> inputs, Map<String, Object> row) {
    // when inputs are empty, send all direct fields
    if (inputs.isEmpty()) {
      return getAllDirectFieldsByKey(row);
    }
    // else, when inputs are not empty
    Map<String, Object> fieldInputsByKey = new LinkedHashMap<>();
    inputs.forEach(input -> putInput(input, row, fieldInputsByKey));
    return fieldInputsByKey;
  }

  /**
   * Builds the CQL column list for a prefetch SELECT from {@code inputs} and the entity metadata.
   *
   * <p>Bare {@code $self} expands to all concrete non-association elements of {@code entity}. Plain
   * direct paths become {@link CQL#get} references; one-level association paths become {@link
   * com.sap.cds.ql.CQL#to(String) CQL.to(...).expand(...)} expands. Deep paths (more than one dot
   * after stripping the {@code $self.} prefix) are skipped. Returns an empty list when {@code
   * inputs} is empty, which the caller interprets as "no column restriction".
   */
  public static List<Selectable> extractSelectables(List<Object> inputs, CdsStructuredType entity) {
    boolean hasBareSelf = inputs.stream().anyMatch(InputExtractor::isBareSelf);
    return inputs.stream()
        .flatMap(
            input -> {
              if (isBareSelf(input)) {
                return entity
                    .concreteNonAssociationElements()
                    .map(e -> (Selectable) CQL.<Object>get(e.getName()));
              }
              String path = extractPath(input);
              if (path == null) return Stream.empty();
              int dot = path.indexOf('.');
              if (dot < 0) {
                // direct field already covered by bare $self expansion — skip to avoid duplicate
                // column
                if (hasBareSelf) return Stream.empty();
                return Stream.of(CQL.<Object>get(path));
              }
              String assoc = path.substring(0, dot);
              String rest = path.substring(dot + 1);
              if (rest.contains(".")) {
                log.warn(
                    "extractSelectables: deep association path '{}' not supported; skipping column",
                    path);
                return Stream.empty();
              }
              return Stream.of(CQL.to(assoc).expand(rest));
            })
        .toList();
  }

  public static String extractPath(Object input) {
    String path = resolvePath(input);
    if (path == null || BARE_SELF.equals(path)) return null;
    return stripSelfPrefix(path);
  }

  public static boolean isBareSelf(Object input) {
    return BARE_SELF.equals(resolvePath(input));
  }

  private static Map<String, Object> getAllDirectFieldsByKey(Map<String, Object> row) {
    Map<String, Object> directFieldsByKey = new LinkedHashMap<>();
    row.forEach(
        (key, fieldValue) -> {
          if (!(fieldValue instanceof Map) && !(fieldValue instanceof Collection<?>))
            directFieldsByKey.put(key, fieldValue);
        });
    return directFieldsByKey;
  }

  private static void putInput(
      Object input, Map<String, Object> row, Map<String, Object> fieldInputsByKey) {
    String path = resolvePath(input);
    if (path != null) {
      if (BARE_SELF.equals(path)) {
        // bare $self with no field — expand all direct fields
        fieldInputsByKey.putAll(getAllDirectFieldsByKey(row));
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
  private static Object getNestedValue(String path, Map<String, Object> nestedValuesByKey) {
    int dot = path.indexOf('.');
    if (dot < 0) {
      return nestedValuesByKey.get(path);
    }
    Object nested = nestedValuesByKey.get(path.substring(0, dot));
    return nested instanceof Map
        ? getNestedValue(path.substring(dot + 1), (Map<String, Object>) nested)
        : null;
  }
}
