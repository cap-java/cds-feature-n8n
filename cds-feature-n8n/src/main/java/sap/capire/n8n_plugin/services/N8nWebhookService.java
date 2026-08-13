/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package sap.capire.n8n_plugin.services;

import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;

/**
 * HTTP layer for calling n8n webhooks.
 *
 * <p>Sends a {@code POST} to {@code baseUrl/path} with the payload as JSON. Auth headers are
 * layered in order: {@code authHeaders} first (e.g. {@code Authorization: Bearer …} from a BTP
 * destination), then {@code X-N8N-API-KEY}. Constructed by {@link
 * sap.capire.n8n_plugin.configuration.N8nAutoConfiguration} with pre-configured timeouts.
 */
public class N8nWebhookService {

  private static final Logger log = LoggerFactory.getLogger(N8nWebhookService.class);

  private final String baseUrl;
  private final String apiKey;
  private final Map<String, String> authHeaders;
  private final RestClient restClient;

  /**
   * @param baseUrl n8n webhook base URL; trailing slash is stripped automatically
   * @param apiKey sent as {@code X-N8N-API-KEY}; may be empty
   * @param authHeaders proxy auth headers (e.g. {@code Authorization: Bearer …} from a BTP
   *     destination); may be empty
   * @param restClient pre-configured {@link RestClient} with connect/read timeouts
   */
  public N8nWebhookService(
      String baseUrl, String apiKey, Map<String, String> authHeaders, RestClient restClient) {
    this.baseUrl = baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
    this.apiKey = apiKey != null ? apiKey : "";
    this.authHeaders = authHeaders != null ? authHeaders : Collections.emptyMap();
    this.restClient = restClient;
  }

  /**
   * POSTs {@code payload} as JSON to {@code baseUrl/path}.
   *
   * @param path webhook path segment appended to the base URL
   * @param payload JSON body sent to n8n
   * @throws org.springframework.web.client.HttpStatusCodeException on HTTP 4xx/5xx responses
   * @throws org.springframework.web.client.ResourceAccessException on network errors or timeouts
   */
  public void notify(String path, Map<String, Object> payload) {
    log.info("Calling n8n webhook path={}", path);
    restClient
        .post()
        .uri(baseUrl + "/" + path)
        .headers(h -> authHeaders.forEach(h::set))
        .header("X-N8N-API-KEY", apiKey)
        .contentType(MediaType.APPLICATION_JSON)
        .body(payload)
        .retrieve()
        .toBodilessEntity();
  }
}
