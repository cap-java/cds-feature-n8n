# Architecture Decision Record

## Webhook Authentication: Single Shared Header vs. Configurable Per-Trigger Headers

| .            | .                    |
|--------------|----------------------|
| Date         | 2026-08-13           |
| Version      | V0.1                 |
| Status       | Draft                |
| Acceptance   | Accepted             |
| Contributors | Lisa Nebel           |
| Reviewers    |                      |

**Version History**

| Version | Date       | Changes         |
|---------|------------|-----------------|
| V0.1    | 2026-08-13 | Initial version |

## Summary

The cds-feature-n8n plugin needs to authenticate outbound webhook calls from CAP to n8n. Two approaches were considered: a single shared header (`X-N8N-API-KEY`) sent on all requests, or configurable per-trigger headers. The single shared header approach was chosen for simplicity, with the architecture intentionally designed to allow per-trigger headers to be added later without breaking changes.

## Context

When a CAP entity event fires (CREATE, UPDATE, DELETE), the plugin sends a request with a JSON payload to an n8n Webhook node. Here, n8n's Webhook node supports optional authentication via Header Auth, Basic Auth, or JWT.

Separately, n8n's admin REST API (`/api/v1/workflows`, `/api/v1/executions`, etc.) is protected by a key called `X-N8N-API-KEY`; such keys can be created within the n8n instance at `/settings/api`.
As of now, this does not yet support calls to that REST API, but possibly in the future.

The question is how the plugin should authenticate these outbound requests — both calls to the webhook nodes and REST API calls — and whether a single credential model can cover both.

## Key Assumptions and Boundary Conditions

- The plugin must work for both self-hosted n8n (direct HTTP, no proxy) and SAP managed n8n (BTP reverse proxy in front of n8n requiring a Bearer token).
- This Java plugin should work like the corresponding Node plugin at https://github.com/cap-js/n8n/.
- Setup should be simple: ideally one credential configured once, covering all webhooks.
- The architecture must not prevent adding more fine-grained auth options for webhooks later.
- For managed n8n on BTP, there is a proxy layer that requires `Authorization: Bearer ...` before the request reaches n8n itself. This is a separate credential independent of the n8n API key.

## Solutions Considered

**Option 1: Single shared header (`X-N8N-API-KEY`)**

One API key is configured globally. The plugin sends `X-N8N-API-KEY: <key>` on every outbound request — so for calls to webhooks and in the future to REST API calls. On the n8n Webhook node, the workflow author can of course still leave the webhook unauthenticated from n8n side but also configure a Header Auth credential with `Name: X-N8N-API-KEY`.

- ✅ Simple setup: one key, one header, everywhere
- ✅ Same header name works for both webhook auth and REST API auth — no cognitive overhead
- ✅ Node.js and Java plugins are aligned
- ❌ All webhooks share the same secret — no per-webhook isolation
- ❌ Header name is hardcoded — if a workflow author uses a different header name on their Webhook node, the plugin cannot reach it without code changes
- ❌ Does not naturally extend to the BTP proxy case without additional `authHeaders` layering

**Option 2: Configurable per-trigger headers**

The `@n8n.process.start` annotation accepts an optional `headers` map per trigger entry, e.g.:

```cds
@n8n.process.start: [{
  on: 'UPDATE',
  path: 'book-updated',
  headers: { 'X-My-Secret': 'abc123' }
}]
```

- ✅ Full flexibility: different secrets per webhook, any header name
- ✅ Decouples the plugin from the `X-N8N-API-KEY` naming convention
- ❌ More complex annotation syntax and handler implementation
- ❌ Secrets in CDS annotations are not ideal — would need env var interpolation support

## Decision

Option 1 (single shared header) was chosen as the default behaviour for now, since n8n's Webhook node does not mandate a header name, workflow authors pick their own. **Aligning on `X-N8N-API-KEY` keeps the setup story simple: one API key, one header, everywhere.**

This matches the Node plugin at https://github.com/cap-js/n8n/.
For the BTP proxy case, the destination-based configuration already handles the two-layer auth problem: `authHeaders` from the BTP destination (e.g. `Authorization: Bearer ...`) are merged onto the request first, then `X-N8N-API-KEY` is added on top.

## Future Extension

Per-trigger configurable headers (Option 2) are explicitly kept as a future extension option. The Java plugin's `N8nWebhookService.notify()` already accepts a payload map; extending it to also accept per-trigger headers is a localised change. The `@n8n.process.start` annotation syntax would need a new optional `headers` field, and the handler would need to resolve and pass those headers through. This can be added without breaking existing setups (the `headers` field would default to empty).

## Related

- `N8nWebhookService.java` — already accepts `Map<String, String> authHeaders` layered before `X-N8N-API-KEY`
- `N8nAutoConfiguration.java` — destination resolution populates `authHeaders` from BTP destination headers
- Node.js plugin `lib/api/connection.js` — `buildWebhookHeaders()` / `buildApiHeaders()` / `authHeaders` from destination
- n8n REST API docs — `X-N8N-API-KEY` is the required header for `/api/v1/…` endpoints
