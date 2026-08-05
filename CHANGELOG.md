# Change Log

- All notable changes to this project are documented in this file.
- The format is based on [Keep a Changelog](https://keepachangelog.com/).
- This project adheres to [Semantic Versioning](https://semver.org/).

## Version 1.0.0 - TBD

### Changed

- Upgraded to CAP Java 5.0.0
- Replaced Spring Retry (`@Retryable`/`@Backoff`) with CAP persistent outbox for reliable n8n webhook delivery
- Updated all modules to use JDK 21, Spring Boot 4.1.0, and CDS Services 5.0.0