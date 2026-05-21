# cds-feature-n8n

A CAP Java plugin that integrates [SAP Cloud Application Programming Model (CAP)](https://cap.cloud.sap/) applications with [n8n](https://n8n.io/) workflow automation via webhooks.

## About this project

`cds-feature-n8n` is a Spring Boot auto-configuration plugin for CAP Java applications. It listens to CDS events (CREATE, DELETE, and custom actions) annotated with `@n8n.process.start` and fires HTTP POST requests to configured n8n webhook URLs — enabling you to trigger n8n workflows directly from your CAP service layer.

**Features:**
- Annotation-driven: no boilerplate code needed in your service handlers
- Supports entity CRUD events (CREATE, DELETE) and custom actions/functions
- Automatic retry with exponential backoff (3 attempts: 2s → 4s → 8s)
- Optional webhook secret header (`X-Webhook-Secret`) for authentication

## Requirements and Setup

### Prerequisites

- Java 17+
- Maven 3.6.3+
- CAP Java (`cds-services` 4.4.1+)
- Spring Boot 3.x
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
    <version>1.0.0-SNAPSHOT</version>
</dependency>
```

### 2. Configure webhooks

In your application's `application.yaml`, configure a base URL and an optional API key:

```yaml
n8n:
  base-url: http://localhost:5678/webhook-test
  apiKey: ${N8N_API_KEY:}
```

The `path` value in each annotation is appended to `base-url` to form the full webhook URL (e.g. `path: 'book-deleted'` → `http://localhost:5678/webhook-test/book-deleted`). The `apiKey` is sent as the `X-Webhook-Secret` header and is optional.

### 3. Configure retry behavior (optional)

The plugin retries failed webhook calls on network errors and 5xx responses. 4xx responses (e.g. 401 Unauthorized, 400 Bad Request) are not retried. The defaults can be overridden in `application.yaml`:

```yaml
n8n:
  retry:
    max-attempts: 3    # total attempts (first call + retries)
    delay: 2000        # initial delay in milliseconds
    multiplier: 2      # backoff multiplier applied to each subsequent delay
```

## Usage

### Annotation-based Triggering

Annotate entities or actions in your CDS model with `@n8n.process.start`. No additional Java code is needed — the plugin detects the annotation and fires the webhook automatically.

Each trigger entry supports three properties:

| Property | Required | Description |
|----------|----------|-------------|
| `on` | yes | Event name — `CREATE`, `READ`, `UPDATE`, `DELETE`, or the action name |
| `path` | yes | Appended to `n8n.base-url` to form the full webhook URL |
| `inputs` | no | List of fields to include in the payload; omit to send all available fields |

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

When the annotated event fires, the plugin posts the entity data (or the selected `inputs` fields) as a flat JSON object to the configured webhook URL. For example, with the `inputs` list above:

```json
{
  "ID": "abc123",
  "title": "The Hobbit",
  "stock": 42
}
```

Omitting `inputs` sends all available fields from the event context.

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
        books.forEach(book -> n8nService.trigger("CREATE", Map.of(
            "event", "CREATE",
            "entity", "AdminService.Books",
            "data", book
        )));
    }
}
```

The first argument to `.trigger()` must match a key configured under `n8n.webhooks` in `application.yaml`. The second argument is the payload — any `Map<String, Object>` you choose to send.

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

**Start the sample:**

```zsh
cd samples/bookshop
mvn clean package
cds watch
```

The sample configures three webhooks (`CREATE`, `DELETE`, `submitOrder`) pointing to an n8n test instance at `http://localhost:5678`. Start n8n locally or update the URLs in `srv/src/main/resources/application.yaml` before running.

## Support, Feedback, Contributing

This project is open to feature requests/suggestions, bug reports etc. via [GitHub issues](https://github.com/SAP/cap-n8n/issues). Contribution and feedback are encouraged and always welcome. For more information about how to contribute, the project structure, as well as additional contribution information, see our [Contribution Guidelines](CONTRIBUTING.md).

## Security / Disclosure

If you find any bug that may be a security problem, please follow our instructions at [in our security policy](https://github.com/SAP/cap-n8n/security/policy) on how to report it. Please do not create GitHub issues for security-related doubts or problems.

## Code of Conduct

We as members, contributors, and leaders pledge to make participation in our community a harassment-free experience for everyone. By participating in this project, you agree to abide by its [Code of Conduct](https://github.com/cap-java/.github/blob/main/CODE_OF_CONDUCT.md) at all times.

## Licensing

Copyright 2024-2025 SAP SE or an SAP affiliate company and cds-feature-n8n contributors. Please see our [LICENSE](LICENSE) for copyright and license information. Detailed information including third-party components and their licensing/copyright information is available [via the REUSE tool](https://api.reuse.software/info/github.com/SAP/cap-n8n).
