/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package integrationtest;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.equalToJson;
import static com.github.tomakehurst.wiremock.client.WireMock.post;
import static com.github.tomakehurst.wiremock.client.WireMock.postRequestedFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlEqualTo;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.awaitility.Awaitility.await;

import cds.gen.testservice.Categories;
import cds.gen.testservice.Categories_;
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
class N8nAssociationInputIntegrationTest {

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
        post(urlEqualTo("/webhook/item-deleted")).willReturn(aResponse().withStatus(200)));
    wireMock.stubFor(
        post(urlEqualTo("/webhook/item-active-deleted")).willReturn(aResponse().withStatus(200)));
  }

  @Autowired
  @Qualifier(TestService_.CDS_NAME)
  CqnService testService;

  @Test
  void deleteItem_withAssociationInput_payloadContainsExpandedCategoryName() {
    String categoryId = UUID.randomUUID().toString();
    Categories category = Categories.create();
    category.setId(categoryId);
    category.setName("Fiction");
    testService.run(Insert.into(Categories_.CDS_NAME).entry(category));

    String itemId = UUID.randomUUID().toString();
    Items item = Items.create();
    item.setId(itemId);
    item.setTitle("Dune");
    item.setStatus("active");
    item.setCategoryId(categoryId);
    testService.run(Insert.into(Items_.CDS_NAME).entry(item));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () -> wireMock.verify(1, postRequestedFor(urlEqualTo("/webhook/item-created"))));
    wireMock.resetRequests();

    testService.run(Delete.from(Items_.CDS_NAME).matching(Map.of("ID", itemId)));

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () ->
                wireMock.verify(
                    1,
                    postRequestedFor(urlEqualTo("/webhook/item-deleted"))
                        .withRequestBody(
                            equalToJson(
                                "{\"ID\":\""
                                    + itemId
                                    + "\",\"title\":\"Dune\",\"name\":\"Fiction\"}",
                                true,
                                false))));
  }
}
