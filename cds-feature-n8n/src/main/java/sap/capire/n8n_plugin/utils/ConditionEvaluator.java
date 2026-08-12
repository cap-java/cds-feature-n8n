/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
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
 * <p>Supported operators: {@code =}, {@code ==}, {@code !=}, {@code <>}, {@code <}, {@code <=},
 * {@code >}, {@code >=}, {@code in}, {@code like}, {@code between}, {@code is null}, {@code is not
 * null}, {@code not} (prefix unary).
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

    Object xprRaw = expr.get("xpr");
    if (!(xprRaw instanceof List<?> xprList) || xprList.isEmpty()) return true;

    try {
      List<Object> xpr = (List<Object>) xprList;
      return evaluateXpr(xpr, 0, row).result;
    } catch (ClassCastException e) {
      return false;
    }
  }

  /**
   * Recursively evaluates the flat infix {@code xpr} list starting at {@code index}. Returns the
   * boolean result and the index after the last consumed token.
   */
  private static EvalResult evaluateXpr(List<Object> xpr, int index, Map<String, Object> row) {
    if (index >= xpr.size()) return new EvalResult(false, index);

    // --- unary NOT: ["not", {xpr:[...]}] ---
    if ("not".equals(xpr.get(index))) {
      if (index + 1 >= xpr.size()) return new EvalResult(false, xpr.size());
      boolean inner = evaluate(xpr.get(index + 1), row);
      return new EvalResult(!inner, index + 2);
    }

    // --- lhs ---
    Object lhsNode = xpr.get(index);
    if (index + 1 >= xpr.size()) return new EvalResult(false, xpr.size());

    Object opRaw = xpr.get(index + 1);
    if (!(opRaw instanceof String op)) return new EvalResult(false, xpr.size());

    // --- IS NULL / IS NOT NULL: [lhs, "is null"] or [lhs, "is", "not", "null"] ---
    if ("is null".equals(op)) {
      boolean result = resolveNode(lhsNode, row) == null;
      return new EvalResult(result, index + 2);
    }
    if ("is".equals(op)) {
      // expect "not" "null" at index+2 and index+3
      if (index + 3 < xpr.size()
          && "not".equals(xpr.get(index + 2))
          && "null".equals(xpr.get(index + 3))) {
        boolean result = resolveNode(lhsNode, row) != null;
        return new EvalResult(result, index + 4);
      }
      return new EvalResult(false, xpr.size());
    }

    // All remaining operators need at least one rhs token
    if (index + 2 >= xpr.size()) return new EvalResult(false, xpr.size());
    Object rhsNode = xpr.get(index + 2);
    int next = index + 3;

    // --- BETWEEN: [lhs, "between", lo, "and", hi] ---
    boolean result;
    if ("between".equals(op)) {
      // expect "and" at index+3, hi at index+4
      if (next < xpr.size() && "and".equals(xpr.get(next)) && next + 1 < xpr.size()) {
        Object hiNode = xpr.get(next + 1);
        result =
            applyBetween(
                resolveNode(lhsNode, row), resolveNode(rhsNode, row), resolveNode(hiNode, row));
        next = next + 2;
      } else {
        return new EvalResult(false, xpr.size());
      }
    } else {
      result = applyOp(op, resolveNode(lhsNode, row), resolveNode(rhsNode, row));
    }

    // --- AND / OR combinators ---
    while (next < xpr.size()) {
      Object combinatorRaw = xpr.get(next);
      if (!(combinatorRaw instanceof String combinator)) break;
      if (!"and".equals(combinator) && !"or".equals(combinator)) break;
      next++;
      if (next + 1 > xpr.size()) break;
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
    if (m.containsKey("list")) {
      // {list: [{val:a}, {val:b}, ...]} — return as a Java List of resolved values
      List<?> items = (List<?>) m.get("list");
      return items.stream().map(item -> resolveNode(item, row)).toList();
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
      case "=", "==" -> Objects.equals(left, right);
      case "!=", "<>" -> !Objects.equals(left, right);
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
      case "in" -> {
        if (right instanceof Collection<?> col) yield col.contains(left);
        yield false;
      }
      case "like" -> {
        if (left == null || right == null) yield false;
        String pattern = right.toString().replace("%", ".*").replace("_", ".");
        yield left.toString().matches(pattern);
      }
      default -> false;
    };
  }

  @SuppressWarnings({"unchecked", "rawtypes"})
  private static boolean applyBetween(Object value, Object lo, Object hi) {
    if (value == null || lo == null || hi == null) return false;
    Comparable v = toComparable(value);
    Comparable l = toComparable(lo);
    Comparable h = toComparable(hi);
    if (v == null || l == null || h == null) return false;
    try {
      return l.compareTo(v) <= 0 && v.compareTo(h) <= 0;
    } catch (ClassCastException e) {
      return l.toString().compareTo(v.toString()) <= 0 && v.toString().compareTo(h.toString()) <= 0;
    }
  }

  @SuppressWarnings("rawtypes")
  private static Comparable toComparable(Object value) {
    if (value instanceof Comparable<?> c) return c;
    return null;
  }

  private record EvalResult(boolean result, int nextIndex) {}
}
