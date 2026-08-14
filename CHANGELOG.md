# Change Log

- All notable changes to this project are documented in this file.
- The format is based on [Keep a Changelog](https://keepachangelog.com/).
- This project adheres to [Semantic Versioning](https://semver.org/).

## Version 1.0.0 - TBD

### Added

- BTP destination support: configure `n8n.destination` with a BTP destination name to resolve the n8n base URL and proxy auth headers (e.g. `Authorization: Bearer …`) automatically from the BTP destination service. The destination can also carry the `X-N8N-API-KEY` header for webhook authentication — this way, when the n8n instance changes (URL or key), only the BTP destination needs to be updated; no redeployment of the application is required. Requires `cloudplatform-connectivity` on the classpath (`com.sap.cloud.sdk.cloudplatform:cloudplatform-connectivity`, optional). An explicit `n8n.api-key` always overrides any `X-N8N-API-KEY` from the destination.
- Webhook authentication via `X-N8N-API-KEY`: the plugin sends `X-N8N-API-KEY` on every outbound webhook call. Configure the matching n8n Webhook node with Header Auth (`Name: X-N8N-API-KEY`, `Value: <key>`) to protect individual workflows. The same header name also covers the n8n admin REST API, so one credential serves both purposes. Set via `n8n.api-key` / `N8N_API_KEY`.
- Association path inputs: `inputs` entries like `$self.category.name` now expand one-level to-one associations and include the resolved field in the webhook payload. Supported for DELETE and UPDATE events; CREATE events use the raw request data and do not support association paths.
- `@n8n.process.start.inputs` is now optional. When omitted, all scalar fields of the entity row are sent to n8n automatically. Associations and compositions (which expand as nested objects) are excluded. Explicit `inputs` still work as before for selective or aliased payloads.
- `ConsoleN8NWebhookService`: opt-in offline mode that logs webhook calls instead of making HTTP requests. Enable with `n8n.use-console=true` — no `base-url` required. Each call is stored in memory and accessible via `getExecutions()` for use in tests.
- Profile-aware startup validation: when `n8n.base-url` is missing and the `development` profile is active, the plugin warns and falls back to `http://localhost:5678`. In all other profiles it fails fast at startup with a clear error pointing to `N8N_BASE_URL`.

### Changed

- `n8n.base-url` now expects the bare n8n host URL without a `/webhook` suffix (e.g. `http://localhost:5678`). The plugin appends `/webhook` automatically, or `/webhook-test` when `n8n.use-test-webhook=true`. This aligns with the Node.js plugin behaviour. Existing configs that included `/webhook` in the URL need to remove that suffix.
- Upgraded to CAP Java 5.0.0
- Replaced Spring Retry (`@Retryable`/`@Backoff`) with CAP persistent outbox for reliable n8n webhook delivery
- Updated all modules to use JDK 21, Spring Boot 4.1.0, and CDS Services 5.0.0
