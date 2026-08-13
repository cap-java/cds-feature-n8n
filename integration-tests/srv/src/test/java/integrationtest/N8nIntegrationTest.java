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
import cds.gen.testservice.Orders;
import cds.gen.testservice.Orders_;
import cds.gen.testservice.TestService_;
import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.sap.cds.ql.Delete;
import com.sap.cds.ql.Insert;
import com.sap.cds.services.cds.CqnService;
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
class N8nIntegrationTest {

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
  void stubWebhook() {
    wireMock.resetAll();
    wireMock.stubFor(
        post(urlEqualTo("/webhook/item-created")).willReturn(aResponse().withStatus(200)));
    wireMock.stubFor(
        post(urlEqualTo("/webhook/item-deleted")).willReturn(aResponse().withStatus(200)));
    wireMock.stubFor(
        post(urlEqualTo("/webhook/order-created")).willReturn(aResponse().withStatus(200)));
  }

  @Autowired
  @Qualifier(TestService_.CDS_NAME)
  CqnService testService;

  @Test
  void createItem_triggersN8nWebhook_withCorrectPayload() {
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("Test Item");

    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () ->
                wireMock.verify(
                    1,
                    postRequestedFor(urlEqualTo("/webhook/item-created"))
                        .withHeader("X-N8N-API-KEY", equalTo("test-key"))
                        .withRequestBody(
                            equalToJson(
                                "{\"ID\":\"" + id + "\",\"title\":\"Test Item\"}", true, false))));
  }

  @Test
  void deleteItem_triggersN8nWebhook_withCorrectPayload() {
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("Item to Delete");
    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    // wait for the CREATE webhook before proceeding
    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-created"))));
    wireMock.resetRequests();

    testService.run(Delete.from(Items_.CDS_NAME).matching(java.util.Map.of("ID", id)));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () ->
                wireMock.verify(
                    1,
                    postRequestedFor(urlEqualTo("/webhook/item-deleted"))
                        .withHeader("X-N8N-API-KEY", equalTo("test-key"))
                        .withRequestBody(
                            equalToJson(
                                "{\"ID\":\""
                                    + id
                                    + "\",\"title\":\"Item to Delete\",\"name\":null}",
                                true,
                                false))));
  }

  @Test
  void createItem_noAnnotationOnUpdate_doesNotTriggerDeleteWebhook() {
    // Sanity check: update is not annotated, so no n8n call should happen
    String id = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(id);
    item.setTitle("Update Me");
    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-created"))));
    wireMock.resetRequests();

    // Update: not annotated — no webhook expected
    Items update = Items.create();
    update.setId(id);
    update.setTitle("Updated Title");
    testService.run(com.sap.cds.ql.Update.entity(Items_.CDS_NAME).data(update));

    // give the outbox a moment — no call should arrive
    await()
        .during(1, SECONDS)
        .atMost(2, SECONDS)
        .until(() -> wireMock.getAllServeEvents().isEmpty());
  }

  @Test
  void createOrder_noInputsAnnotation_sendsAllScalarFields() {
    String id = UUID.randomUUID().toString();
    Orders order = Orders.create();
    order.setId(id);
    order.setTotal(42);

    testService.run(Insert.into(Orders_.CDS_NAME).entry(order));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () ->
                wireMock.verify(
                    1,
                    postRequestedFor(urlEqualTo("/webhook/order-created"))
                        .withHeader("X-N8N-API-KEY", equalTo("test-key"))
                        .withRequestBody(
                            equalToJson("{\"ID\":\"" + id + "\",\"total\":42}", true, false))));
  }
}
