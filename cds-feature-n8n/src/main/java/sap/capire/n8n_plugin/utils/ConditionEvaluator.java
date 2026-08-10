/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Evaluates a CSN {@code xpr} condition from an {@code @n8n.process.start.if} annotation against a
 * CDS entity row.
 *
 * <p>The CDS compiler serialises {@code if: (status = 'shipped')} as:
 *
 * <pre>{@code
 * {"=": "status = 'shipped'", "xpr": [{"ref": ["status"]}, "=", {"val": "shipped"}]}
 * }</pre>
 *
 * <p>The {@code xpr} list is a flat infix sequence of operands and operators. Operands are either
 * {@code {"ref": [...]}} field references or {@code {"val": v}} literals. Compound expressions use
 * {@code "and"} / {@code "or"} as infix combinators between triples.
 *
 * <p>The CDS Java SDK may return the annotation value as an internal {@code ValueExpression} rather
 * than a plain {@link Map}. In that case, its {@link Object#toString()} produces valid JSON, which
 * is parsed back into a {@link Map} so the evaluator can proceed normally.
 */
public class ConditionEvaluator {

  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ConditionEvaluator() {}

  /**
   * Evaluates the given CSN {@code if} expression against {@code row}.
   *
   * @param ifExpression the value of {@code trigger.get("if")} — a CSN expression map, or {@code
   *     null}
   * @param row the full entity row to evaluate against
   * @return {@code true} if the condition is met or absent; {@code false} if explicitly not met
   */
  @SuppressWarnings("unchecked")
  public static boolean evaluate(Object ifExpression, Map<String, Object> row) {
    if (ifExpression == null) return true;

    Map<?, ?> expr;
    if (ifExpression instanceof Map<?, ?> m) {
      expr = m;
    } else {
      // CDS Java SDK may wrap the annotation value in an internal ValueExpression whose
      // toString() produces valid JSON — parse it back into a plain Map
      try {
        expr = MAPPER.readValue(ifExpression.toString(), Map.class);
      } catch (Exception e) {
        return true;
      }
    }

    List<Object> xpr = (List<Object>) expr.get("xpr");
    if (xpr == null || xpr.isEmpty()) return true;

    return evaluateXpr(xpr, 0, row).result;
  }

  /**
   * Recursively evaluates the flat infix {@code xpr} list starting at {@code index}. Returns the
   * boolean result and the index after the last consumed token.
   */
  private static EvalResult evaluateXpr(List<Object> xpr, int index, Map<String, Object> row) {
    if (index >= xpr.size()) return new EvalResult(true, index);

    // Read the first triple: lhs op rhs
    Object lhsNode = xpr.get(index);
    if (index + 2 >= xpr.size()) return new EvalResult(true, xpr.size());

    String op = (String) xpr.get(index + 1);
    Object rhsNode = xpr.get(index + 2);
    int next = index + 3;

    boolean result = applyOp(op, resolveNode(lhsNode, row), resolveNode(rhsNode, row));

    // Check for "and" / "or" combinator
    while (next < xpr.size()) {
      String combinator = (String) xpr.get(next);
      if (!"and".equals(combinator) && !"or".equals(combinator)) break;
      next++;
      if (next + 2 > xpr.size()) break;
      EvalResult right = evaluateXpr(xpr, next, row);
      result = "and".equals(combinator) ? (result && right.result) : (result || right.result);
      next = right.nextIndex;
    }

    return new EvalResult(result, next);
  }

  @SuppressWarnings("unchecked")
  private static Object resolveNode(Object node, Map<String, Object> row) {
    if (!(node instanceof Map<?, ?> m)) return node;
    if (m.containsKey("val")) return m.get("val");
    if (m.containsKey("ref")) {
      List<String> path = (List<String>) m.get("ref");
      return getNestedValue(path, row);
    }
    return null;
  }

  @SuppressWarnings("unchecked")
  private static Object getNestedValue(List<String> path, Map<String, Object> row) {
    Map<String, Object> current = row;
    for (int i = 0; i < path.size() - 1; i++) {
      Object next = current.get(path.get(i));
      if (!(next instanceof Map<?, ?> nested)) return null;
      current = (Map<String, Object>) nested;
    }
    return current.get(path.get(path.size() - 1));
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static boolean applyOp(String op, Object left, Object right) {
    return switch (op) {
      case "=" -> Objects.equals(left, right);
      case "!=" -> !Objects.equals(left, right);
      case "<", ">", "<=", ">=" -> {
        if (left == null || right == null) yield false;
        Comparable l = toComparable(left);
        Comparable r = toComparable(right);
        if (l == null || r == null) yield false;
        int cmp;
        try {
          cmp = l.compareTo(r);
        } catch (ClassCastException e) {
          cmp = l.toString().compareTo(r.toString());
        }
        yield switch (op) {
          case "<" -> cmp < 0;
          case ">" -> cmp > 0;
          case "<=" -> cmp <= 0;
          case ">=" -> cmp >= 0;
          default -> false;
        };
      }
      default -> true;
    };
  }

  @SuppressWarnings("rawtypes")
  private static Comparable toComparable(Object value) {
    if (value instanceof Comparable<?> c) return c;
    return null;
  }

  private record EvalResult(boolean result, int nextIndex) {}
}
