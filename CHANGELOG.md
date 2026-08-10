# Change Log

- All notable changes to this project are documented in this file.
- The format is based on [Keep a Changelog](https://keepachangelog.com/).
- This project adheres to [Semantic Versioning](https://semver.org/).

## Version 1.0.0 - TBD

### Added

- `ConsoleN8NWebhookService`: opt-in offline mode that logs webhook calls instead of making HTTP requests. Enable with `n8n.use-console=true` — no `base-url` required. Each call is stored in memory and accessible via `getExecutions()` for use in tests.
- Profile-aware startup validation: when `n8n.base-url` is missing and the `development` profile is active, the plugin warns and falls back to `http://localhost:5678/webhook`. In all other profiles it fails fast at startup with a clear error pointing to `N8N_BASE_URL`.

### Changed

- Upgraded to CAP Java 5.0.0
- Replaced Spring Retry (`@Retryable`/`@Backoff`) with CAP persistent outbox for reliable n8n webhook delivery
- Updated all modules to use JDK 21, Spring Boot 4.1.0, and CDS Services 5.0.0