# SAP Cloud Application Programming Model, n8n plugin for Java

[![REUSE status](https://api.reuse.software/badge/github.com/cap-java/cds-feature-n8n)](https://api.reuse.software/info/github.com/cap-java/cds-feature-n8n)

CAP Plugin to automatically trigger and interact with n8n workflow automation tool.

## Table of Contents

- [About this project](#about-this-project)
- [Requirements and Setup](#requirements-and-setup)
  - [1. Add the dependency](#1-add-the-dependency)
  - [2. Configure webhooks](#2-configure-webhooks)
  - [3. Configure retry behavior (optional)](#3-configure-retry-behavior-optional)
  - [Local Development Setup](#local-development-setup)
  - [Console Mode (Offline / CI)](#console-mode-offline--ci)
- [Usage](#usage)
  - [Annotation-based Triggering](#annotation-based-triggering)
  - [Conditional triggering with `if`](#conditional-triggering-with-if)
  - [Programmatic Triggering](#programmatic-triggering)
- [Tests](#tests)
- [Support, Feedback, Contributing](#support-feedback-contributing)
- [Security / Disclosure](#security--disclosure)
- [Code of Conduct](#code-of-conduct)
- [Licensing](#licensing)

## About this project

`cds-feature-n8n` is a Spring Boot auto-configuration plugin for CAP Java applications. It listens to CDS events (CREATE, DELETE, and custom actions) annotated with `@n8n.process.start` and fires HTTP POST requests to configured n8n webhook URLs — enabling you to trigger n8n workflows directly from your CAP service layer.

**Features:**
- Annotation-driven: no boilerplate code needed in your service handlers
- Supports entity CRUD events (CREATE, DELETE) and custom actions/functions
- Automatic retry with exponential backoff (3 attempts: 2s → 4s → 8s)
- Optional webhook secret header (`X-Webhook-Secret`) for authentication

## Requirements and Setup

### Prerequisites

- Java 21+
- Maven 3.6.3+
- CAP Java (`cds-services`) 5.0.0 or higher
- Spring Boot 4.1.0 or higher
- A running n8n instance

### 1. Add the dependency

Build and install the plugin locally:

```zsh
mvn clean install
```

Then add it to your CAP Java application's `pom.xml`:

```xml
<dependency>
    <groupId>sap.capire</groupId>
    <artifactId>cds-feature-n8n</artifactId>
    <version>0.0.1-alpha</version>
</dependency>
```

### 2. Configure webhooks

In your application's `application.yaml`, configure a base URL and an optional API key:

```yaml
n8n:
  base-url: http://localhost:5678/webhook-test
  api-key: ${N8N_API_KEY:}
```

The `path` value in each annotation is appended to `base-url` to form the full webhook URL (e.g. `path: 'book-deleted'` → `http://localhost:5678/webhook-test/book-deleted`). The `api-key` is sent as the `X-Webhook-Secret` header and is optional.

### 3. Configure retry behavior (optional)

The plugin retries failed webhook calls only on **network-level errors** — when n8n is unreachable (connection refused, timeout). HTTP error responses are not retried:

| Response | Meaning | Retried? |
|----------|---------|----------|
| Network error / timeout | n8n is down or unreachable | Yes |
| 5xx | n8n responded but the workflow itself failed | No |
| 4xx | Misconfiguration (wrong URL, bad auth) | No |

Retry behavior is managed by the CAP persistent outbox. Configure it under `cds.outbox.services.N8nOutbox` in your `application.yaml`:

```yaml
cds:
  outbox:
    services:
      N8nOutbox:
        maxAttempts: 10   # total attempts before the message is marked as failed
        ordered: true     # process messages in submission order (default: true)
```

---

### Local Development Setup

Follow these steps to get a fully working local environment from scratch.

**Step 1 — Build and install the plugin**

From the project root:

```zsh
mvn clean install
```

**Step 2 — Start n8n with Docker**

```zsh
docker volume create n8n_data

docker run -it --rm \
  --name n8n \
  -p 5678:5678 \
  -e GENERIC_TIMEZONE="Europe/Berlin" \
  -e TZ="Europe/Berlin" \
  -e N8N_ENFORCE_SETTINGS_FILE_PERMISSIONS=true \
  -e N8N_RUNNERS_ENABLED=true \
  -v n8n_data:/home/node/.n8n \
  docker.n8n.io/n8nio/n8n
```

Replace `Europe/Berlin` with your local timezone (e.g. `America/New_York`). The named volume `n8n_data` persists your workflows across container restarts. n8n will be available at `http://localhost:5678`.

> **First run only:** open `http://localhost:5678` in a browser and create an owner account before proceeding.

**Step 3 — Start the sample app**

```zsh
cd samples/bookshop/srv
mvn spring-boot:run
```

The app starts on `http://localhost:8080` and points at `http://localhost:5678/webhook` by default. Any annotated CDS event will fire a webhook to your local n8n instance.

**Step 3a — Smoke test**

1. In n8n, create a workflow with a **Webhook** node, path `book-deleted`, save (Cmd+S), click **"Listen for Test Event"**
2. Delete any book at `http://localhost:8080` → Admin → Books
3. n8n should show a green execution with `{ "ID": "...", "title": "...", "author_ID": "..." }`

> **Alternative (test mode):** If you prefer one-shot manual testing, set `n8n.use-test-webhook: true` in `application.yaml`, restart the app, then click **"Listen for Test Event"** in n8n instead of activating the workflow.

**Step 4 — Secure the webhook**

`N8N_API_KEY` is a shared secret sent as `X-Webhook-Secret` on every webhook POST — not the n8n REST API key under Settings → n8n API.
Set `N8N_API_KEY` in your environment (or `~/.zshrc`) and configure the n8n Webhook node with **Authentication: Header Auth**, Name: `X-Webhook-Secret`, Value: same string.

Without it, n8n must have **Authentication: None** — otherwise it returns 403.

**Test vs. production webhooks**

| Mode | n8n URL | When to use |
|------|---------|-------------|
| Production (default) | `/webhook` | Workflows are active and handle every call |
| Test | `/webhook-test` | One-off manual testing; requires clicking "Listen for Test Event" in the n8n UI each time, and cannot handle bulk calls |

Toggle test mode via `use-test-webhook` in `application.yaml`:

```yaml
n8n:
  use-test-webhook: true   # set to false (default) for production webhooks
```

### Console Mode (Offline / CI)

The plugin ships a built-in console mode for local development and CI environments where no n8n instance is available. When enabled, webhook calls are **logged instead of POSTed** — the app behaves normally but never makes an HTTP request to n8n.

#### Enabling console mode

Add `n8n.use-console: true` to your `application.yaml`:

```yaml
n8n:
  use-console: true
```

To scope it to a specific Spring profile, put it in the matching profile file (e.g. `application-test.yaml`):

```yaml
n8n:
  use-console: true
```

No `base-url` is needed. Console mode takes precedence over all other configuration.

#### What you see in the logs

```
INFO ConsoleN8NWebhookService - [console-n8n-service]: would POST /webhook/book-deleted - payload: {ID=abc123, title=The Hobbit, author_ID=...}
```

#### Using console mode in tests

Inject `ConsoleN8NWebhookService` to assert on webhook calls without a real n8n instance:

```java
@SpringBootTest
@TestPropertySource(properties = "n8n.use-console=true")
class MyServiceTest {

    @Autowired
    ConsoleN8NWebhookService consoleWebhookService;

    @Test
    void deleteBook_triggersWebhook() {
        // ... trigger a delete ...

        assertThat(consoleWebhookService.getExecutions()).hasSize(1);
        Map<String, Object> exec = consoleWebhookService.getExecutions().get(0);
        assertThat(exec.get("path")).isEqualTo("book-deleted");
        assertThat(exec.get("status")).isEqualTo("success");
    }
}
```

Each execution record contains: `id`, `path`, `payload`, `startedAt`, `finishedAt`, `status`.

#### Missing base-url behaviour (without console mode)

If `n8n.use-console` is `false` (the default) and `n8n.base-url` is not set:

| Profile | Behaviour |
|---------|-----------|
| `development` | Warns at startup and falls back to `http://localhost:5678/webhook`. HTTP calls fail gracefully if n8n is not running — the outbox retries with backoff. |
| any other | Throws `IllegalStateException` at startup: set `N8N_BASE_URL` or use `n8n.use-console=true`. |

## Usage

### Annotation-based Triggering

Annotate entities or actions in your CDS model with `@n8n.process.start`. No additional Java code is needed — the plugin detects the annotation and fires the webhook automatically.

Each trigger entry supports three properties:

| Property | Required | Description |
|----------|----------|-------------|
| `on` | yes | Event name — `CREATE`, `READ`, `UPDATE`, `DELETE`, or the action name |
| `path` | yes | Appended to `n8n.base-url` to form the full webhook URL |
| `inputs` | no | Fields to include in the payload; defaults to all direct entity attributes when omitted |

**Entity events (CRUD):**

```cds
annotate AdminService.Books with @n8n.process.start: [
  {on: 'DELETE', path: 'book-deleted', inputs: [$self.ID, $self.title, $self.stock]}
];
```

**For custom actions:**

```cds
annotate CatalogService.submitOrder with @n8n.process.start.on: 'submitOrder'
                                        @n8n.process.start.path: 'order-submitted';
```

When the annotated event fires, the plugin posts the selected `inputs` fields as a flat JSON object to the configured webhook URL. For example, with the `inputs` list above:

```json
{
  "ID": "abc123",
  "title": "The Hobbit",
  "stock": 42
}
```

When `inputs` is omitted, all scalar fields of the entity are included in the payload. Specify `inputs` explicitly to limit which fields are sent — useful to avoid exposing sensitive or large fields.

**Association fields** can be included using dot notation — the plugin issues a single expanded query to fetch the associated data:

```cds
annotate AdminService.Books with @n8n.process.start: [
  {on: 'DELETE', path: 'book-deleted', inputs: [$self.ID, $self.title, $self.author.name]}
];
```

This produces a payload with the leaf field name as the key:

```json
{
  "ID": "abc123",
  "title": "The Hobbit",
  "name": "Tolkien"
}
```

> **Note:** Only one level of association traversal is supported (`$self.author.name`). Deeper paths (`$self.author.address.city`) are skipped with a warning. This is a known limitation compared to the Node.js plugin — contributions welcome.
>
> **Note:** Association paths are *not* resolved for `CREATE` events. For these, the plugin uses the raw request payload (the data as submitted), so association fields like `$self.author.name` will be `null`. Use scalar FK fields (e.g. `$self.author_ID`) for `CREATE` triggers instead.

Multiple trigger entries for the same event on the same entity are supported — all matching entries fire. This allows you to route to different n8n workflows from one event, optionally with different `if` conditions.

### Conditional triggering with `if`

Add an `if` expression to a trigger entry to fire the webhook only when the condition is met:

```cds
annotate AdminService.Books with @n8n.process.start: [
  {on: 'DELETE', path: 'book-deleted',   if: (stock = 0), inputs: [$self.ID, $self.title, $self.author_ID]},
  {on: 'UPDATE', path: 'book-updated',   inputs: [$self.ID, $self.title]},
  {on: 'UPDATE', path: 'book-low-stock', inputs: [$self.ID, $self.title, $self.stock],
                                          if: (stock < 10)}
];
```

Only deletes where stock is 0 fire `book-deleted`; every update fires `book-updated`; only updates that bring stock below 10 also fire `book-low-stock` — e.g. to trigger a reorder workflow in n8n.

The `if` property is optional. When present, the webhook fires only when the condition evaluates to true against the entity row:

| Property | Required | Description |
|----------|----------|-------------|
| `if` | no | CDS expression — webhook fires only when the condition is true |

Supported operators: `=`, `==`, `!=`, `<>`, `<`, `<=`, `>`, `>=`, `in`, `like`, `between`, `is null`, `is not null`, `not`, `and`, `or`.

> **Note:** The `if` condition is evaluated in application code against the entity row available at the time the event fires — it is not pushed to the database. This means it uses the same data the plugin already has: the CQN payload for CREATE, and the prefetched row for UPDATE and DELETE. Complex expressions involving subqueries or navigation paths that aren't part of the prefetched columns will not work.

### Programmatic Triggering

For cases where you need full control over when and what is sent, inject `N8nService` directly into any CAP event handler and call `.trigger()`:

```java
@Component
@ServiceName(AdminService_.CDS_NAME)
public class AdminServiceHandler implements EventHandler {

    @Autowired
    private N8nService n8nService;

    @After(event = CqnService.EVENT_CREATE, entity = "AdminService.Books")
    public void afterCreateBook(List<Books> books) {
        books.forEach(book -> n8nService.trigger("book-created", Map.of(
            "ID", book.getId(),
            "title", book.getTitle()
        )));
    }
}
```

The first argument to `.trigger()` is the webhook path — appended to `n8n.base-url` to form the full URL (e.g. `"book-created"` → `http://localhost:5678/webhook/book-created`). The second argument is the payload — any `Map<String, Object>` you choose to send.

> **Note:** The annotation-based and programmatic approaches are independent. You can use both in the same application, but take care not to fire duplicate webhooks for the same event.

## Tests

Run the unit tests from the project root:

```zsh
mvn test
```

To also generate a JaCoCo coverage report (unit tests only):

```zsh
mvn verify
```

To include the retry integration test in coverage:

```zsh
mvn verify -pl cds-feature-n8n -am -Dtest="N8nHandlerTest,N8nWebhookServiceRetryIT"
```

The report is written to `cds-feature-n8n/target/site/jacoco/index.html`.

### Integration tests

The retry integration test (`N8nWebhookServiceRetryIT`) is excluded from the default `mvn test` run because Maven Surefire skips `*IT.java` classes by default. Run it explicitly:

```zsh
mvn test -pl cds-feature-n8n -am -Dtest=N8nWebhookServiceRetryIT
```

This test uses [WireMock](https://wiremock.org) to start a local HTTP server that stands in for n8n, verifying that the retry logic fires the webhook up to three times before giving up.

### Testing with the sample application

The `samples/bookshop` directory contains a complete CAP bookshop app that demonstrates the plugin with real webhook triggers.

For a full walkthrough including starting n8n locally in Docker, see [Local Development Setup](#local-development-setup).

**Quick start (assumes n8n is already running):**

```zsh
cd samples/bookshop/srv
mvn spring-boot:run
```

The sample configures three webhook triggers on `AdminService.Books`: `DELETE` with `if: (stock = 0)` fires `book-deleted`; every `UPDATE` fires `book-updated`; and updates that bring stock below 10 also fire `book-low-stock`. See [Local Development Setup](#local-development-setup) for the full walkthrough. 404 → listener expired or workflow not saved. 403 → `X-Webhook-Secret` mismatch.

## Support, Feedback, Contributing

This project is open to feature requests/suggestions, bug reports etc. via [GitHub issues](https://github.com/cap-java/cds-feature-n8n/issues). Contribution and feedback are encouraged and always welcome. For more information about how to contribute, the project structure, as well as additional contribution information, see our [Contribution Guidelines](CONTRIBUTING.md).

## Security / Disclosure

If you find any bug that may be a security problem, please follow our instructions at [in our security policy](https://github.com/cap-java/cds-feature-n8n/security/policy) on how to report it. Please do not create GitHub issues for security-related doubts or problems.

## Code of Conduct

We as members, contributors, and leaders pledge to make participation in our community a harassment-free experience for everyone. By participating in this project, you agree to abide by its [Code of Conduct](https://github.com/cap-java/.github/blob/main/CODE_OF_CONDUCT.md) at all times.

## Licensing

Copyright 2026 SAP SE or an SAP affiliate company and cds-feature-n8n contributors. Please see our [LICENSE](LICENSE) for copyright and license information. Detailed information including third-party components and their licensing/copyright information is available [via the REUSE tool](https://api.reuse.software/info/github.com/cap-java/cds-feature-n8n).
