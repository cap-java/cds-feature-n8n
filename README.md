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

In your application's `application.yaml`, define a webhook entry for each named trigger:

```yaml
n8n:
  webhooks:
    submitOrder:
      url: http://localhost:5678/webhook-test/order-created
      apiKey: ${N8N_API_KEY:}
    CREATE:
      url: http://localhost:5678/webhook-test/book-created
      apiKey: ${N8N_API_KEY:}
```

The `apiKey` value is sent as the `X-Webhook-Secret` header. It is optional.

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

**For entity events (CREATE / DELETE):**

```cds
annotate AdminService.Books with @n8n.process.start: [
  {on: 'CREATE'},
  {on: 'DELETE'}
];
```

**For custom actions:**

```cds
annotate CatalogService.submitOrder with @n8n.process.start.on: 'submitOrder';
```

When the annotated event fires, the plugin sends a JSON payload to the configured webhook URL:

```json
{
  "event": "CREATE",
  "entity": "AdminService.Books",
  "user": "alice",
  "data": { ... }
}
```

## Tests

Run the unit tests from the project root:

```zsh
mvn test
```

To also generate a JaCoCo coverage report:

```zsh
mvn verify
```

The report is written to `cds-feature-n8n/target/site/jacoco/index.html`.

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
