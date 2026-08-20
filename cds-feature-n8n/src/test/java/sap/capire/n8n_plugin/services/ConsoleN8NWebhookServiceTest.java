/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ConsoleN8NWebhookServiceTest {

  private ConsoleN8NWebhookService service;

  @BeforeEach
  void setUp() {
    service = new ConsoleN8NWebhookService();
  }

  @Test
  void notify_doesNotThrow() {
    assertThatCode(() -> service.notify("my-path", Map.of("ID", "1"), "POST"))
        .doesNotThrowAnyException();
  }

  @Test
  void notify_addsExecutionRecord() {
    service.notify("book-created", Map.of("ID", "42", "title", "Dune"), "POST");

    assertThat(service.getExecutions()).hasSize(1);
    Map<String, Object> exec = service.getExecutions().get(0);
    assertThat(exec)
        .containsEntry("path", "book-created")
        .containsEntry("method", "POST")
        .containsEntry("payload", Map.of("ID", "42", "title", "Dune"))
        .containsEntry("status", "success")
        .containsEntry("id", "console-exec-1")
        .containsKey("startedAt")
        .containsKey("finishedAt");
  }

  @Test
  void notify_multipleCallsGetDistinctIncrementingIds() {
    service.notify("path-a", Map.of("k", "1"), "POST");
    service.notify("path-b", Map.of("k", "2"), "PUT");
    service.notify("path-c", Map.of("k", "3"), "DELETE");

    assertThat(service.getExecutions()).hasSize(3);
    assertThat(service.getExecutions().get(0)).containsEntry("id", "console-exec-1");
    assertThat(service.getExecutions().get(1)).containsEntry("id", "console-exec-2");
    assertThat(service.getExecutions().get(2)).containsEntry("id", "console-exec-3");
  }

  @Test
  void notify_customMethod_recordedInExecution() {
    service.notify("book-updated", Map.of("ID", "1"), "PATCH");

    Map<String, Object> exec = service.getExecutions().get(0);
    assertThat(exec).containsEntry("method", "PATCH");
  }
}
