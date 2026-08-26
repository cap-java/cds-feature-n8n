# Change Log

- All notable changes to this project are documented in this file.
- The format is based on [Keep a Changelog](https://keepachangelog.com/).
- This project adheres to [Semantic Versioning](https://semver.org/).

## Version 0.0.1

### Added

- Annotation-driven n8n webhook triggers via `@n8n.process.start` — no boilerplate service handler code required
- Supports CDS entity events (`CREATE`, `READ`, `UPDATE`, `DELETE`) and custom actions/functions as trigger sources
- Configurable HTTP method per trigger (`GET`, `POST`, `PUT`, `PATCH`, `DELETE`, `HEAD`) via the `method` property — defaults to `POST`
- Reliable webhook delivery via CAP persistent outbox with configurable retry
- Webhook authentication via `X-N8N-API-KEY` header, configured through `n8n.api-key` / `N8N_API_KEY`
- BTP destination support: configure `n8n.destination` to resolve the n8n base URL and authentication headers from the BTP Destination Service — no redeployment needed when the n8n instance changes
- Optional `inputs` payload selection on `@n8n.process.start`; when omitted, all direct entity fields are sent automatically
- Association path inputs (e.g. `$self.category.name`) for including resolved to-one association fields in the webhook payload
- `ConsoleN8NWebhookService`: offline/test mode that logs webhook calls instead of making HTTP requests — enable with `n8n.use-console=true`
- Profile-aware startup validation: warns and falls back to `http://localhost:5678` in the `development` profile; fails fast in all other profiles when `n8n.base-url` is not set
- `n8n.base-url` expects the bare n8n host (e.g. `http://localhost:5678`) — the plugin appends `/webhook` automatically, or `/webhook-test` when `n8n.use-test-webhook=true`
- Programmatic API via the `n8n` service for triggering workflows from custom handler code
