/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.services;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

import java.util.Map;
import org.junit.jupiter.api.Test;

class ConsoleN8NWebhookServiceTest {

  private final ConsoleN8NWebhookService service = new ConsoleN8NWebhookService();

  @Test
  void notify_doesNotThrow() {
    assertDoesNotThrow(() -> service.notify("my-path", Map.of("ID", "1")));
  }

  @Test
  void notify_addsExecutionRecord() {
    service.notify("book-created", Map.of("ID", "42", "title", "Dune"));

    assertThat(service.getExecutions()).hasSize(1);
    Map<String, Object> exec = service.getExecutions().get(0);
    assertThat(exec.get("path")).isEqualTo("book-created");
    assertThat(exec.get("payload")).isEqualTo(Map.of("ID", "42", "title", "Dune"));
    assertThat(exec.get("status")).isEqualTo("success");
    assertThat(exec.get("executionId")).isEqualTo("console-exec-1");
    assertThat(exec.get("startedAt")).isNotNull();
    assertThat(exec.get("finishedAt")).isNotNull();
  }

  @Test
  void notify_multipleCallsGetDistinctIncrementingIds() {
    service.notify("path-a", Map.of("k", "1"));
    service.notify("path-b", Map.of("k", "2"));
    service.notify("path-c", Map.of("k", "3"));

    assertThat(service.getExecutions()).hasSize(3);
    assertThat(service.getExecutions().get(0).get("executionId")).isEqualTo("console-exec-1");
    assertThat(service.getExecutions().get(1).get("executionId")).isEqualTo("console-exec-2");
    assertThat(service.getExecutions().get(2).get("executionId")).isEqualTo("console-exec-3");
  }
}
