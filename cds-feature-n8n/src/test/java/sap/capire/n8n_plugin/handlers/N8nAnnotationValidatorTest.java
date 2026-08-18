/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.handlers;

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
