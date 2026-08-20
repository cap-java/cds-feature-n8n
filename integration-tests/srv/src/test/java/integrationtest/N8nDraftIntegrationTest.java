/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package integrationtest;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultHandlers.print;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import sap.capire.n8n_plugin.services.ConsoleN8NWebhookService;

/**
 * Verifies that @n8n.process.start on a draft-enabled entity fires exactly once — on draftActivate,
 * not on draft creation.
 */
@SpringBootTest(classes = app.Application.class)
@WithMockUser(username = "admin", roles = "admin")
@TestPropertySource(properties = "n8n.use-console=true")
class N8nDraftIntegrationTest {

  @Autowired WebApplicationContext wac;
  @Autowired ConsoleN8NWebhookService consoleWebhookService;

  private final ObjectMapper objectMapper = new ObjectMapper();

  MockMvc mockMvc;

  @BeforeEach
  void setUp() {
    mockMvc = MockMvcBuilders.webAppContextSetup(wac).apply(springSecurity()).build();
    consoleWebhookService.getExecutions().clear();
  }

  @Test
  void draftCreate_doesNotTriggerWebhook_onlyDraftActivateDoes() throws Exception {
    // Step 1: create a draft — should NOT fire the webhook
    String createResponse =
        mockMvc
            .perform(
                post("/odata/v4/TestService/DraftBooks")
                    .contentType(MediaType.APPLICATION_JSON)
                    .content("{\"title\":\"My Draft Book\"}"))
            .andDo(print())
            .andExpect(status().isCreated())
            .andReturn()
            .getResponse()
            .getContentAsString();

    @SuppressWarnings("unchecked")
    Map<String, Object> created = objectMapper.readValue(createResponse, Map.class);
    String id = (String) created.get("ID");

    // No webhook should have fired yet
    assertThat(consoleWebhookService.getExecutions())
        .as("webhook must NOT fire on draft creation")
        .isEmpty();

    // Step 2: activate the draft — should fire exactly one webhook
    mockMvc
        .perform(
            post("/odata/v4/TestService/DraftBooks(ID="
                    + id
                    + ",IsActiveEntity=false)/TestService.draftActivate")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
        .andDo(print())
        .andExpect(status().isOk());

    await()
        .atMost(5, SECONDS)
        .untilAsserted(
            () ->
                assertThat(consoleWebhookService.getExecutions())
                    .as("webhook must fire exactly once after draftActivate")
                    .hasSize(1));

    Map<String, Object> exec = consoleWebhookService.getExecutions().get(0);
    assertThat(exec).containsEntry("path", "draft-book-created");
    @SuppressWarnings("unchecked")
    Map<String, Object> payload = (Map<String, Object>) exec.get("payload");
    assertThat(payload).containsEntry("ID", id).containsEntry("title", "My Draft Book");
  }
}
