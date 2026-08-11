/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.utils;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

class InputExtractorTest {

  @Test
  void extract_plainStringInput_returnsField() {
    Map<String, Object> row = Map.of("ID", "1", "title", "Dune");
    Map<String, Object> result = InputExtractor.extract(List.of("ID", "title"), row);
    assertThat(result).containsEntry("ID", "1").containsEntry("title", "Dune");
  }

  @Test
  void extract_csnMapForm_returnsField() {
    // CDS annotation compiler emits {"=": "fieldName"} for path expressions
    Map<String, Object> row = Map.of("stock", 42);
    Map<String, Object> result = InputExtractor.extract(List.of(Map.of("=", "stock")), row);
    assertThat(result).containsEntry("stock", 42);
  }

  @Test
  void extract_selfPrefixIsStripped() {
    Map<String, Object> row = Map.of("title", "Dune");
    Map<String, Object> result = InputExtractor.extract(List.of("$self.title"), row);
    assertThat(result).containsEntry("title", "Dune");
  }

  @Test
  void extract_nestedPath_walksMap() {
    Map<String, Object> row = Map.of("author", Map.of("name", "Frank Herbert"));
    Map<String, Object> result = InputExtractor.extract(List.of("author.name"), row);
    // leaf key of "author.name" is "name"
    assertThat(result).containsEntry("name", "Frank Herbert");
  }

  @Test
  void extract_nestedPath_missingSegment_returnsNull() {
    Map<String, Object> row = Map.of("author", "not-a-map");
    Map<String, Object> result = InputExtractor.extract(List.of("author.name"), row);
    assertThat(result).containsEntry("name", null);
  }

  @Test
  void extract_structFormWithAs_usesAlias() {
    Map<String, Object> row = Map.of("title", "Dune");
    Map<String, Object> spec = Map.of("path", "title", "as", "bookTitle");
    Map<String, Object> result = InputExtractor.extract(List.of(spec), row);
    assertThat(result).containsEntry("bookTitle", "Dune").doesNotContainKey("title");
  }

  @Test
  void extract_structFormWithoutAs_usesLeafKey() {
    Map<String, Object> row = Map.of("title", "Dune");
    Map<String, Object> spec = Map.of("path", "title");
    Map<String, Object> result = InputExtractor.extract(List.of(spec), row);
    assertThat(result).containsEntry("title", "Dune");
  }

  @Test
  void extract_structFormWithCsnMapPath_resolvesCorrectly() {
    Map<String, Object> row = Map.of("stock", 5);
    Map<String, Object> spec = Map.of("path", Map.of("=", "stock"), "as", "qty");
    Map<String, Object> result = InputExtractor.extract(List.of(spec), row);
    assertThat(result).containsEntry("qty", 5);
  }

  @Test
  void extract_missingField_returnsNull() {
    Map<String, Object> row = Map.of("ID", "1");
    Map<String, Object> result = InputExtractor.extract(List.of("nonexistent"), row);
    assertThat(result).containsEntry("nonexistent", null);
  }

  @Test
  void extract_emptyInputs_returnsAllScalarFields() {
    Map<String, Object> row = Map.of("ID", "1", "title", "Dune", "stock", 42);
    Map<String, Object> result = InputExtractor.extract(List.of(), row);
    assertThat(result)
        .containsEntry("ID", "1")
        .containsEntry("title", "Dune")
        .containsEntry("stock", 42);
  }

  @Test
  void extract_emptyInputs_excludesAssociationsAndCompositions() {
    Map<String, Object> row = new java.util.LinkedHashMap<>();
    row.put("ID", "1");
    row.put("title", "Dune");
    row.put("author", Map.of("name", "Frank Herbert"));
    Map<String, Object> result = InputExtractor.extract(List.of(), row);
    assertThat(result)
        .containsEntry("ID", "1")
        .containsEntry("title", "Dune")
        .doesNotContainKey("author");
  }

  @Test
  void extract_emptyInputs_excludesToManyAssociations() {
    Map<String, Object> row = new java.util.LinkedHashMap<>();
    row.put("ID", "1");
    row.put("title", "Dune");
    row.put("items", List.of(Map.of("ID", "2"), Map.of("ID", "3")));
    Map<String, Object> result = InputExtractor.extract(List.of(), row);
    assertThat(result)
        .containsEntry("ID", "1")
        .containsEntry("title", "Dune")
        .doesNotContainKey("items");
  }

  @Test
  void extract_emptyInputs_excludesCollectionFields() {
    Map<String, Object> row = new java.util.LinkedHashMap<>();
    row.put("ID", "1");
    row.put("tags", List.of("sci-fi", "classic"));
    Map<String, Object> result = InputExtractor.extract(List.of(), row);
    assertThat(result).containsEntry("ID", "1").doesNotContainKey("tags");
  }

  @Test
  void extract_unknownInputType_isIgnored() {
    Map<String, Object> result = InputExtractor.extract(List.of(42), Map.of("ID", "1"));
    assertThat(result).isEmpty();
  }
}
