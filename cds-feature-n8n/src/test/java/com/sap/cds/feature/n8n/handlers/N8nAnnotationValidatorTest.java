/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package com.sap.cds.feature.n8n.handlers;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import com.sap.cds.reflect.CdsEntity;
import com.sap.cds.reflect.CdsModel;
import java.util.List;
import java.util.Map;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class N8nAnnotationValidatorTest {

  @Mock CdsModel cdsModel;
  @Mock CdsEntity entity;

  @InjectMocks N8nAnnotationValidator validator;

  @Test
  void throwsWhenFirstElementMissingOn() {
    when(cdsModel.entities()).thenReturn(Stream.of(entity));
    when(entity.getAnnotationValue(N8nHandler.ANNOTATION_START, List.of()))
        .thenReturn(List.of(Map.of("path", "my-wf"))); // missing "on"
    when(entity.getQualifiedName()).thenReturn("TestService.Books");

    assertThatThrownBy(() -> validator.validateN8nAnnotations(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("[0]")
        .hasMessageContaining("TestService.Books")
        .hasMessageContaining("'on'");
  }

  @Test
  void throwsOnCorrectIndexWhenSecondElementMissingOn() {
    when(cdsModel.entities()).thenReturn(Stream.of(entity));
    when(entity.getAnnotationValue(N8nHandler.ANNOTATION_START, List.of()))
        .thenReturn(
            List.of(
                Map.of("path", "wf-ok", "on", "CREATE"),
                Map.of("path", "wf-bad"))); // index 1 missing "on"

    assertThatThrownBy(() -> validator.validateN8nAnnotations(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("[1]");
  }

  @Test
  void throwsWhenElementMissingPath() {
    when(cdsModel.entities()).thenReturn(Stream.of(entity));
    when(entity.getAnnotationValue(N8nHandler.ANNOTATION_START, List.of()))
        .thenReturn(List.of(Map.of("on", "CREATE"))); // missing "path"
    when(entity.getQualifiedName()).thenReturn("TestService.Books");

    assertThatThrownBy(() -> validator.validateN8nAnnotations(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("[0]")
        .hasMessageContaining("TestService.Books")
        .hasMessageContaining("'path'");
  }

  @Test
  void passesWhenAllElementsHaveOn() {
    when(cdsModel.entities()).thenReturn(Stream.of(entity));
    when(entity.getAnnotationValue(N8nHandler.ANNOTATION_START, List.of()))
        .thenReturn(
            List.of(
                Map.of("path", "wf-created", "on", "CREATE"),
                Map.of("path", "wf-deleted", "on", "DELETE")));

    assertThatCode(() -> validator.validateN8nAnnotations(null)).doesNotThrowAnyException();
  }

  @Test
  void passesWhenValidMethodSpecified() {
    when(cdsModel.entities()).thenReturn(Stream.of(entity));
    when(entity.getAnnotationValue(N8nHandler.ANNOTATION_START, List.of()))
        .thenReturn(List.of(Map.of("path", "wf", "on", "CREATE", "method", "PUT")));

    assertThatCode(() -> validator.validateN8nAnnotations(null)).doesNotThrowAnyException();
  }

  @Test
  void throwsWhenMethodIsInvalidString() {
    when(cdsModel.entities()).thenReturn(Stream.of(entity));
    when(entity.getAnnotationValue(N8nHandler.ANNOTATION_START, List.of()))
        .thenReturn(List.of(Map.of("path", "wf", "on", "CREATE", "method", "YOLO")));
    when(entity.getQualifiedName()).thenReturn("TestService.Books");

    assertThatThrownBy(() -> validator.validateN8nAnnotations(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("YOLO")
        .hasMessageContaining("Allowed values");
  }

  @Test
  void throwsWhenMethodIsNotAString() {
    when(cdsModel.entities()).thenReturn(Stream.of(entity));
    when(entity.getAnnotationValue(N8nHandler.ANNOTATION_START, List.of()))
        .thenReturn(List.of(Map.of("path", "wf", "on", "CREATE", "method", 42)));
    when(entity.getQualifiedName()).thenReturn("TestService.Books");

    assertThatThrownBy(() -> validator.validateN8nAnnotations(null))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("expected a String");
  }

  @Test
  void passesWhenMethodAbsent_defaultsToPost() {
    when(cdsModel.entities()).thenReturn(Stream.of(entity));
    when(entity.getAnnotationValue(N8nHandler.ANNOTATION_START, List.of()))
        .thenReturn(List.of(Map.of("path", "wf", "on", "CREATE")));

    assertThatCode(() -> validator.validateN8nAnnotations(null)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @ValueSource(strings = {"GET", "POST", "PUT", "PATCH", "DELETE", "HEAD"})
  void passesForAllAllowedMethods(String method) {
    when(cdsModel.entities()).thenReturn(Stream.of(entity));
    when(entity.getAnnotationValue(N8nHandler.ANNOTATION_START, List.of()))
        .thenReturn(List.of(Map.of("path", "wf", "on", "CREATE", "method", method)));

    assertThatCode(() -> validator.validateN8nAnnotations(null)).doesNotThrowAnyException();
  }

  @ParameterizedTest
  @CsvSource({
    "CREATE, POST",
    "CREATE, GET",
    "CREATE, PUT",
    "CREATE, PATCH",
    "CREATE, DELETE",
    "READ,   POST",
    "UPDATE, PATCH",
    "UPDATE, PUT",
    "DELETE, DELETE",
    "DELETE, POST",
  })
  void passesForAllEventAndMethodCombinations(String on, String method) {
    when(cdsModel.entities()).thenReturn(Stream.of(entity));
    when(entity.getAnnotationValue(N8nHandler.ANNOTATION_START, List.of()))
        .thenReturn(List.of(Map.of("path", "wf", "on", on.trim(), "method", method.trim())));

    assertThatCode(() -> validator.validateN8nAnnotations(null)).doesNotThrowAnyException();
  }

  @Test
  void passesWhenAnnotationIsAbsent() {
    when(cdsModel.entities()).thenReturn(Stream.of(entity));
    when(entity.getAnnotationValue(N8nHandler.ANNOTATION_START, List.of())).thenReturn(List.of());

    assertThatCode(() -> validator.validateN8nAnnotations(null)).doesNotThrowAnyException();
  }

  @Test
  void passesWhenNoEntitiesInModel() {
    when(cdsModel.entities()).thenReturn(Stream.of());

    assertThatCode(() -> validator.validateN8nAnnotations(null)).doesNotThrowAnyException();
  }
}
