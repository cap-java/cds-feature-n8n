/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package integrationtest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import cds.gen.testservice.Items;
import cds.gen.testservice.Items_;
import cds.gen.testservice.TestService_;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.sap.cds.ql.Delete;
import com.sap.cds.ql.Insert;
import com.sap.cds.services.cds.CqnService;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

@SpringBootTest(classes = app.Application.class)
@WithMockUser(username = "admin", roles = "admin")
class N8nIfConditionIntegrationTest {

  static WireMockServer wireMock;

  @BeforeAll
  static void startWireMock() {
    wireMock = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
    wireMock.start();
  }

  @AfterAll
  static void stopWireMock() {
    wireMock.stop();
  }

  @DynamicPropertySource
  static void n8nBaseUrl(DynamicPropertyRegistry registry) {
    registry.add("n8n.base-url", () -> "http://localhost:" + wireMock.port() + "/webhook");
    registry.add("n8n.api-key", () -> "test-key");
  }

  @BeforeEach
  void stubWebhooks() {
    wireMock.resetAll();
    wireMock.stubFor(
        post(urlEqualTo("/webhook/item-created")).willReturn(aResponse().withStatus(200)));
    wireMock.stubFor(
        post(urlEqualTo("/webhook/item-shipped")).willReturn(aResponse().withStatus(200)));
    wireMock.stubFor(
        post(urlEqualTo("/webhook/item-deleted")).willReturn(aResponse().withStatus(200)));
    wireMock.stubFor(
        post(urlEqualTo("/webhook/item-active-deleted")).willReturn(aResponse().withStatus(200)));
  }

  @Autowired
  @Qualifier(TestService_.CDS_NAME)
  CqnService testService;

  // --- CREATE with if condition ---

  @Test
  void createItem_ifConditionMet_firesConditionalWebhook() {
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("Shipped Book");
    item.setStatus("shipped");

    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () ->
                wireMock.verify(
                    1,
                    postRequestedFor(urlEqualTo("/webhook/item-shipped"))
                        .withHeader("X-Webhook-Secret", equalTo("test-key"))
                        .withRequestBody(
                            equalToJson(
                                "{\"ID\":\"" + id + "\",\"status\":\"shipped\"}", true, false))));
  }

  @Test
  void createItem_ifConditionNotMet_doesNotFireConditionalWebhook() {
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("Pending Book");
    item.setStatus("pending");

    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    // wait for the unconditional webhook — once the outbox processes this, it has
    // already evaluated the conditional trigger too and skipped it
    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-created"))));

    // conditional webhook must not have fired
    wireMock.verify(0, postRequestedFor(urlEqualTo("/webhook/item-shipped")));
  }

  @Test
  void createItem_ifConditionMet_bothUnconditionalAndConditionalWebhookFire() {
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("Shipped Book");
    item.setStatus("shipped");

    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> {
              wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-created")));
              wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-shipped")));
            });
  }

  // --- cross-event isolation ---

  @Test
  void createItem_doesNotFireDeleteWebhooks() {
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("New Item");
    item.setStatus("active");

    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-created"))));

    wireMock.verify(0, postRequestedFor(urlEqualTo("/webhook/item-deleted")));
    wireMock.verify(0, postRequestedFor(urlEqualTo("/webhook/item-active-deleted")));
  }

  @Test
  void deleteItem_doesNotFireCreateWebhooks() {
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("Item to Delete");
    item.setStatus("active");
    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-created"))));
    wireMock.resetRequests();

    testService.run(Delete.from(Items_.CDS_NAME).matching(Map.of("ID", id)));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-deleted"))));

    wireMock.verify(0, postRequestedFor(urlEqualTo("/webhook/item-created")));
    wireMock.verify(0, postRequestedFor(urlEqualTo("/webhook/item-shipped")));
  }

  @Test
  void createThenDelete_eachEventFiresOnlyItsOwnWebhooks() {
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("Lifecycle Item");
    item.setStatus("shipped"); // meets CREATE if (= 'shipped') and DELETE if (!= 'draft')

    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> {
              wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-created")));
              wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-shipped")));
            });
    // no delete webhooks should have fired during create
    wireMock.verify(0, postRequestedFor(urlEqualTo("/webhook/item-deleted")));
    wireMock.verify(0, postRequestedFor(urlEqualTo("/webhook/item-active-deleted")));
    wireMock.resetRequests();

    testService.run(Delete.from(Items_.CDS_NAME).matching(Map.of("ID", id)));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> {
              wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-deleted")));
              wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-active-deleted")));
            });
    // no create webhooks should have fired during delete
    wireMock.verify(0, postRequestedFor(urlEqualTo("/webhook/item-created")));
    wireMock.verify(0, postRequestedFor(urlEqualTo("/webhook/item-shipped")));
  }

  // --- DELETE with if condition ---

  @Test
  void deleteItem_ifConditionMet_firesConditionalWebhook() {
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("Active Item");
    item.setStatus("active");
    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-created"))));
    wireMock.resetRequests();

    testService.run(Delete.from(Items_.CDS_NAME).matching(Map.of("ID", id)));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () ->
                wireMock.verify(
                    1,
                    postRequestedFor(urlEqualTo("/webhook/item-active-deleted"))
                        .withHeader("X-Webhook-Secret", equalTo("test-key"))
                        .withRequestBody(
                            equalToJson(
                                "{\"ID\":\"" + id + "\",\"status\":\"active\"}", true, false))));
  }

  @Test
  void deleteItem_ifConditionNotMet_doesNotFireConditionalWebhook() {
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("Draft Item");
    item.setStatus("draft");
    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-created"))));
    wireMock.resetRequests();

    testService.run(Delete.from(Items_.CDS_NAME).matching(Map.of("ID", id)));

    // unconditional delete webhook fires — once processed, the conditional trigger was also
    // evaluated
    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-deleted"))));

    // conditional delete webhook must not have fired
    wireMock.verify(0, postRequestedFor(urlEqualTo("/webhook/item-active-deleted")));
  }
}
