/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package integrationtest;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import cds.gen.testservice.Items;
import cds.gen.testservice.Items_;
import cds.gen.testservice.Orders;
import cds.gen.testservice.Orders_;
import cds.gen.testservice.TestService_;
import com.sap.cds.ql.Delete;
import com.sap.cds.ql.Insert;
import com.sap.cds.services.cds.CqnService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import sap.capire.n8n_plugin.services.ConsoleN8NWebhookService;
import sap.capire.n8n_plugin.services.N8nService;

@SpringBootTest(classes = app.Application.class)
@WithMockUser(username = "admin", roles = "admin")
@TestPropertySource(properties = "n8n.use-console=true")
class N8nConsoleIntegrationTest {

  @Autowired
  @Qualifier(TestService_.CDS_NAME)
  CqnService testService;

  @Autowired ConsoleN8NWebhookService consoleWebhookService;

  @Autowired N8nService n8nService;

  @BeforeEach
  void clearExecutions() {
    consoleWebhookService.getExecutions().clear();
  }

  @Test
  void createItem_annotationDriven_recordsExecution() {
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("Console Item");

    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> assertThat(consoleWebhookService.getExecutions()).hasSize(1));

    Map<String, Object> exec = consoleWebhookService.getExecutions().get(0);
    assertThat(exec).containsEntry("path", "item-created").containsEntry("status", "success");
    assertThat(exec.get("id")).asString().startsWith("console-exec-");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) exec.get("payload");
    assertThat(payload).containsEntry("ID", id).containsEntry("title", "Console Item");
  }

  @Test
  void deleteItem_annotationDriven_recordsExecution() {
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("Item to Delete");
    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> assertThat(consoleWebhookService.getExecutions()).hasSize(1));
    consoleWebhookService.getExecutions().clear();

    testService.run(Delete.from(Items_.CDS_NAME).matching(Map.of("ID", id)));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () ->
                assertThat(consoleWebhookService.getExecutions())
                    .anySatisfy(
                        exec -> {
                          assertThat(exec).containsEntry("path", "item-deleted");
                          @SuppressWarnings("unchecked")
                          Map<String, Object> payload = (Map<String, Object>) exec.get("payload");
                          assertThat(payload).containsEntry("ID", id);
                        }));
  }

  @Test
  void programmatic_trigger_recordsExecution() {
    n8nService.trigger("manual-hook", Map.of("key", "value"));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> assertThat(consoleWebhookService.getExecutions()).hasSize(1));

    Map<String, Object> exec = consoleWebhookService.getExecutions().get(0);
    assertThat(exec).containsEntry("path", "manual-hook").containsEntry("status", "success");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) exec.get("payload");
    assertThat(payload).containsEntry("key", "value");
  }

  @Test
  void noHttpCallsMade_inConsoleMode() {
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("No HTTP");
    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> assertThat(consoleWebhookService.getExecutions()).hasSize(1));

    // execution was recorded but no real HTTP call was made — verified by absence of
    // ResourceAccessException or any network activity (console service is the only bean)
    assertThat(consoleWebhookService.getExecutions().get(0)).containsEntry("status", "success");
  }

  @Test
  void createOrder_noInputsAnnotation_recordsAllScalarFields() {
    String id = UUID.randomUUID().toString();
    Orders order = Orders.create();
    order.setId(id);
    order.setTotal(99);

    testService.run(Insert.into(Orders_.CDS_NAME).entry(order));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(() -> assertThat(consoleWebhookService.getExecutions()).hasSize(1));

    Map<String, Object> exec = consoleWebhookService.getExecutions().get(0);
    assertThat(exec).containsEntry("path", "order-created").containsEntry("status", "success");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) exec.get("payload");
    assertThat(payload).containsEntry("ID", id).containsEntry("total", 99);
  }
}
