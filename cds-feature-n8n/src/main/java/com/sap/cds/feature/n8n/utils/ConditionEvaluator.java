/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package com.sap.cds.feature.n8n.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
 */
public class ConditionEvaluator {

  private static final Logger log = LoggerFactory.getLogger(ConditionEvaluator.class);
  private static final ObjectMapper MAPPER = new ObjectMapper();

  private ConditionEvaluator() {}

  /** Extracts the {@code if} expression from a trigger map, or {@code null} if absent. */
  public static Object extractIf(Map<String, Object> trigger) {
    return trigger.get("if");
  }

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
      try {
        expr = MAPPER.readValue(ifExpression.toString(), Map.class);
      } catch (Exception e) {
        log.warn("ConditionEvaluator: failed to parse if-expression, skipping webhook dispatch", e);
        return false;
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

    if ("not".equals(xpr.get(index))) {
      return evaluateNot(xpr, index, row);
    }

    Object lhsNode = xpr.get(index);
    if (index + 1 >= xpr.size()) return new EvalResult(false, xpr.size());
    Object opRaw = xpr.get(index + 1);
    if (!(opRaw instanceof String op)) return new EvalResult(false, xpr.size());

    EvalResult nullCheck = evaluateNullCheck(op, lhsNode, xpr, index, row);
    if (nullCheck != null) return nullCheck;

    if (index + 2 >= xpr.size()) return new EvalResult(false, xpr.size());
    Object rhsNode = xpr.get(index + 2);
    int next = index + 3;

    boolean result;
    if ("between".equals(op)) {
      EvalResult between = evaluateBetween(lhsNode, rhsNode, xpr, next, row);
      if (between == null) return new EvalResult(false, xpr.size());
      result = between.result;
      next = between.nextIndex;
    } else {
      result = applyOp(op, resolveNode(lhsNode, row), resolveNode(rhsNode, row));
    }

    return applyCombinatorsFrom(xpr, next, result, row);
  }

  private static EvalResult evaluateNot(List<Object> xpr, int index, Map<String, Object> row) {
    if (index + 1 >= xpr.size()) return new EvalResult(false, xpr.size());
    boolean inner = evaluate(xpr.get(index + 1), row);
    return new EvalResult(!inner, index + 2);
  }

  private static EvalResult evaluateNullCheck(
      String op, Object lhsNode, List<Object> xpr, int index, Map<String, Object> row) {
    if ("is null".equals(op)) {
      return new EvalResult(resolveNode(lhsNode, row) == null, index + 2);
    }
    if ("is".equals(op)) {
      if (index + 3 < xpr.size()
          && "not".equals(xpr.get(index + 2))
          && "null".equals(xpr.get(index + 3))) {
        return new EvalResult(resolveNode(lhsNode, row) != null, index + 4);
      }
      return new EvalResult(false, xpr.size());
    }
    return null;
  }

  private static EvalResult evaluateBetween(
      Object lhsNode, Object loNode, List<Object> xpr, int next, Map<String, Object> row) {
    if (next >= xpr.size() || !"and".equals(xpr.get(next)) || next + 1 >= xpr.size()) return null;
    Object hiNode = xpr.get(next + 1);
    boolean result =
        applyBetween(resolveNode(lhsNode, row), resolveNode(loNode, row), resolveNode(hiNode, row));
    return new EvalResult(result, next + 2);
  }

  private static EvalResult applyCombinatorsFrom(
      List<Object> xpr, int start, boolean initial, Map<String, Object> row) {
    boolean result = initial;
    int next = start;
    while (isCombinator(xpr, next) && next + 1 < xpr.size()) {
      String combinator = (String) xpr.get(next);
      next++;
      EvalResult right = evaluateXpr(xpr, next, row);
      result = "and".equals(combinator) ? (result && right.result) : (result || right.result);
      next = right.nextIndex;
    }
    return new EvalResult(result, next);
  }

  private static boolean isCombinator(List<Object> xpr, int index) {
    if (index >= xpr.size()) return false;
    Object token = xpr.get(index);
    return "and".equals(token) || "or".equals(token);
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
      case "=", "==" -> left != null && right != null && Objects.equals(left, right);
      case "!=", "<>" -> left != null && right != null && !Objects.equals(left, right);
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
        // Escape regex metacharacters first, then translate SQL wildcards.
        // Without this, a pattern like 'v1.0%' would treat '.' as "any char" in regex.
        String escaped = right.toString().replaceAll("[.+^${}()|\\[\\]\\\\]", "\\\\$0");
        String pattern = escaped.replace("%", ".*").replace("_", ".");
        yield left.toString().matches(pattern);
      }
      default -> {
        log.warn("ConditionEvaluator: unsupported operator '{}', skipping webhook dispatch", op);
        yield false;
      }
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
