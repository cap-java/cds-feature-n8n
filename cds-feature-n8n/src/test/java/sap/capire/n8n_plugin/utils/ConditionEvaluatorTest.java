/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class ConditionEvaluatorTest {

  // Builds the CSN xpr map the CDS compiler emits for a condition expression
  private static Map<String, Object> xpr(Object... tokens) {
    return Map.of("xpr", List.of(tokens));
  }

  private static Map<String, Object> ref(String... path) {
    return Map.of("ref", List.of(path));
  }

  private static Map<String, Object> val(Object v) {
    return Map.of("val", v);
  }

  // --- null / absent condition ---

  @Test
  void nullExpression_returnsTrue() {
    assertThat(ConditionEvaluator.evaluate(null, Map.of())).isTrue();
  }

  @Test
  void nonMapExpression_unparseable_returnsFalse() {
    assertThat(ConditionEvaluator.evaluate("unexpected-string", Map.of())).isFalse();
  }

  @Test
  void emptyXpr_returnsTrue() {
    assertThat(ConditionEvaluator.evaluate(Map.of("xpr", List.of()), Map.of())).isTrue();
  }

  // --- equality ---

  @Test
  void equality_matchingStringField_returnsTrue() {
    var expr = xpr(ref("status"), "=", val("shipped"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "shipped"))).isTrue();
  }

  @Test
  void equality_nonMatchingStringField_returnsFalse() {
    var expr = xpr(ref("status"), "=", val("shipped"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "pending"))).isFalse();
  }

  @Test
  void equality_missingFieldInRow_returnsFalse() {
    var expr = xpr(ref("status"), "=", val("shipped"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of())).isFalse();
  }

  // --- inequality ---

  @Test
  void notEqual_nonMatchingField_returnsTrue() {
    var expr = xpr(ref("status"), "!=", val("shipped"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "pending"))).isTrue();
  }

  @Test
  void notEqual_matchingField_returnsFalse() {
    var expr = xpr(ref("status"), "!=", val("shipped"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "shipped"))).isFalse();
  }

  // --- numeric comparisons ---

  @Test
  void lessThan_belowThreshold_returnsTrue() {
    var expr = xpr(ref("stock"), "<", val(5));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("stock", 3))).isTrue();
  }

  @Test
  void lessThan_aboveThreshold_returnsFalse() {
    var expr = xpr(ref("stock"), "<", val(5));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("stock", 10))).isFalse();
  }

  @Test
  void greaterThan_aboveThreshold_returnsTrue() {
    var expr = xpr(ref("stock"), ">", val(0));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("stock", 5))).isTrue();
  }

  @Test
  void lessThanOrEqual_equalValue_returnsTrue() {
    var expr = xpr(ref("stock"), "<=", val(5));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("stock", 5))).isTrue();
  }

  @Test
  void greaterThanOrEqual_equalValue_returnsTrue() {
    var expr = xpr(ref("stock"), ">=", val(5));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("stock", 5))).isTrue();
  }

  @Test
  void comparison_nullLeft_returnsFalse() {
    var expr = xpr(ref("stock"), ">", val(0));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of())).isFalse();
  }

  // --- and / or combinators ---

  @Test
  void and_bothTrue_returnsTrue() {
    var expr = xpr(ref("status"), "=", val("shipped"), "and", ref("stock"), ">", val(0));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "shipped", "stock", 5))).isTrue();
  }

  @Test
  void and_oneFalse_returnsFalse() {
    var expr = xpr(ref("status"), "=", val("shipped"), "and", ref("stock"), ">", val(0));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "shipped", "stock", 0)))
        .isFalse();
  }

  @Test
  void or_oneTrue_returnsTrue() {
    var expr = xpr(ref("status"), "=", val("shipped"), "or", ref("status"), "=", val("delivered"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "delivered"))).isTrue();
  }

  @Test
  void or_bothFalse_returnsFalse() {
    var expr = xpr(ref("status"), "=", val("shipped"), "or", ref("status"), "=", val("delivered"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "pending"))).isFalse();
  }

  // --- == and <> aliases ---

  @Test
  void doubleEqual_matchingField_returnsTrue() {
    var expr = xpr(ref("status"), "==", val("shipped"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "shipped"))).isTrue();
  }

  @Test
  void diamond_nonMatchingField_returnsTrue() {
    var expr = xpr(ref("status"), "<>", val("shipped"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "pending"))).isTrue();
  }

  // --- IN ---

  @Test
  void in_valueInList_returnsTrue() {
    var expr = xpr(ref("status"), "in", Map.of("list", List.of(val("shipped"), val("delivered"))));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "shipped"))).isTrue();
  }

  @Test
  void in_valueNotInList_returnsFalse() {
    var expr = xpr(ref("status"), "in", Map.of("list", List.of(val("shipped"), val("delivered"))));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "pending"))).isFalse();
  }

  @Test
  void in_nullValue_returnsFalse() {
    var expr = xpr(ref("status"), "in", Map.of("list", List.of(val("shipped"))));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of())).isFalse();
  }

  // --- LIKE ---

  @Test
  void like_wildcardPercent_matches_returnsTrue() {
    var expr = xpr(ref("title"), "like", val("Cap%"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("title", "Captain"))).isTrue();
  }

  @Test
  void like_wildcardPercent_noMatch_returnsFalse() {
    var expr = xpr(ref("title"), "like", val("Cap%"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("title", "Book"))).isFalse();
  }

  @Test
  void like_underscoreWildcard_matches_returnsTrue() {
    var expr = xpr(ref("code"), "like", val("A_C"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("code", "ABC"))).isTrue();
  }

  @Test
  void like_nullValue_returnsFalse() {
    var expr = xpr(ref("title"), "like", val("Cap%"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of())).isFalse();
  }

  @Test
  void like_patternWithDot_doesNotMatchArbitraryChar() {
    var expr = xpr(ref("version"), "like", val("v1.0%"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("version", "v1X0-beta"))).isFalse();
  }

  @Test
  void like_patternWithDot_matchesLiteralDot() {
    var expr = xpr(ref("version"), "like", val("v1.0%"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("version", "v1.0-beta"))).isTrue();
  }

  // --- BETWEEN ---

  @Test
  void between_valueInRange_returnsTrue() {
    var expr = xpr(ref("stock"), "between", val(5), "and", val(10));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("stock", 7))).isTrue();
  }

  @Test
  void between_valueBelowRange_returnsFalse() {
    var expr = xpr(ref("stock"), "between", val(5), "and", val(10));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("stock", 3))).isFalse();
  }

  @Test
  void between_valueAboveRange_returnsFalse() {
    var expr = xpr(ref("stock"), "between", val(5), "and", val(10));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("stock", 15))).isFalse();
  }

  @Test
  void between_valueBoundary_returnsTrue() {
    var expr = xpr(ref("stock"), "between", val(5), "and", val(10));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("stock", 5))).isTrue();
  }

  @Test
  void between_nullValue_returnsFalse() {
    var expr = xpr(ref("stock"), "between", val(5), "and", val(10));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of())).isFalse();
  }

  // --- IS NULL / IS NOT NULL ---

  @Test
  void isNull_nullField_returnsTrue() {
    var expr = xpr(ref("deletedAt"), "is null");
    Map<String, Object> row = new HashMap<>();
    row.put("deletedAt", null);
    assertThat(ConditionEvaluator.evaluate(expr, row)).isTrue();
  }

  @Test
  void isNull_missingField_returnsTrue() {
    var expr = xpr(ref("deletedAt"), "is null");
    assertThat(ConditionEvaluator.evaluate(expr, Map.of())).isTrue();
  }

  @Test
  void isNull_presentField_returnsFalse() {
    var expr = xpr(ref("deletedAt"), "is null");
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("deletedAt", "2026-01-01"))).isFalse();
  }

  @Test
  void isNotNull_presentField_returnsTrue() {
    var expr = xpr(ref("deletedAt"), "is", "not", "null");
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("deletedAt", "2026-01-01"))).isTrue();
  }

  @Test
  void isNotNull_nullField_returnsFalse() {
    var expr = xpr(ref("deletedAt"), "is", "not", "null");
    Map<String, Object> row = new HashMap<>();
    row.put("deletedAt", null);
    assertThat(ConditionEvaluator.evaluate(expr, row)).isFalse();
  }

  // --- NOT (prefix unary) ---

  @Test
  void not_trueCondition_returnsFalse() {
    var inner = xpr(ref("status"), "=", val("shipped"));
    var expr = Map.of("xpr", List.of("not", inner));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "shipped"))).isFalse();
  }

  @Test
  void not_falseCondition_returnsTrue() {
    var inner = xpr(ref("status"), "=", val("shipped"));
    var expr = Map.of("xpr", List.of("not", inner));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("status", "pending"))).isTrue();
  }

  // --- nested field reference ---

  @Test
  void nestedRef_matchingValue_returnsTrue() {
    var expr = xpr(ref("author", "name"), "=", val("Tolkien"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of("author", Map.of("name", "Tolkien"))))
        .isTrue();
  }

  @Test
  void nestedRef_missingIntermediateSegment_returnsFalse() {
    var expr = xpr(ref("author", "name"), "=", val("Tolkien"));
    assertThat(ConditionEvaluator.evaluate(expr, Map.of())).isFalse();
  }
}
