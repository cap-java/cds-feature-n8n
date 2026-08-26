/*
* © 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors.
*/
package com.sap.cds.feature.n8n.services;

import java.util.Collections;
import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * HTTP layer for calling n8n webhooks.
 *
 * <p>Sends a {@code POST} to {@code baseUrl/path} with the payload as JSON. Auth headers are
 * layered in order: {@code authHeaders} first (e.g. {@code Authorization: Bearer …} from a BTP
 * destination), then {@code X-N8N-API-KEY}. Constructed by {@link
 * com.sap.cds.feature.n8n.configuration.N8nAutoConfiguration} with pre-configured timeouts.
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
   * Sends {@code payload} to {@code baseUrl/path} using the given HTTP method.
   *
   * <p>For bodyless methods ({@code GET}, {@code HEAD}) the payload is serialized as query
   * parameters. For all other methods it is sent as a JSON body with {@code Content-Type:
   * application/json}.
   *
   * @param path webhook path segment appended to the base URL
   * @param payload fields to send — as query params for GET/HEAD, as JSON body otherwise
   * @param httpMethod HTTP method to use (e.g. {@code POST}, {@code PUT}, {@code DELETE})
   * @throws org.springframework.web.client.HttpStatusCodeException on HTTP 4xx/5xx responses
   * @throws org.springframework.web.client.ResourceAccessException on network errors or timeouts
   */
  public void notify(String path, Map<String, Object> payload, HttpMethod httpMethod) {
    log.info(
        "Calling n8n webhook path={}, payload={}, method={}", path, payload.keySet(), httpMethod);
    boolean useQueryParams = httpMethod == HttpMethod.GET || httpMethod == HttpMethod.HEAD;
    RestClient.RequestBodySpec spec =
        restClient
            .method(httpMethod)
            .uri(useQueryParams ? buildUriWithParams(path, payload) : baseUrl + "/" + path)
            .headers(
                header -> {
                  authHeaders.forEach(header::set);
                  if (!apiKey.isEmpty()) header.set("X-N8N-API-KEY", apiKey);
                });
    if (useQueryParams) {
      spec.retrieve().toBodilessEntity();
    } else {
      spec.contentType(MediaType.APPLICATION_JSON).body(payload).retrieve().toBodilessEntity();
    }
  }

  private String buildUriWithParams(String path, Map<String, Object> payload) {
    UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl + "/" + path);
    payload.forEach((k, v) -> builder.queryParam(k, v != null ? v.toString() : ""));
    return builder.build().toUriString();
  }
}
