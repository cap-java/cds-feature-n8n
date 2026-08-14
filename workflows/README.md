# Example n8n Workflows

Ready-to-import n8n workflows for the bookshop sample application.

## How to import

1. Open n8n at `http://localhost:5678`
2. Go to **Workflows → Add workflow → Import from file**
3. Select any `.json` file from this directory
4. Save (Cmd+S) and **Activate** the workflow

## Workflows

### `book-created.json` — Book Created (alert)

**Trigger:** CAP fires `POST /webhook/book-created` after every book is created via `AdminService`.

**Payload received:**
```json
{ "ID": "...", "title": "...", "stock": 42 }
```

**What it does:**
- Checks whether stock is ≤ 0
  - **Yes →** builds a low-stock alert object (subject, bookId, bookTitle, alertType)
  - **No →** logs the creation as a plain message

No credentials needed.

---

### `book-created-confirmation.json` — Book Created (confirmation)

**Trigger:** CAP fires `POST /webhook/book-created` after every book is created via `AdminService`. Uses Header Auth to verify the request.

**Payload received:**
```json
{ "body": { "ID": "...", "title": "...", "stock": 42 } }
```

**What it does:**
1. Calls back into the bookshop via `POST /odata/v4/admin/AdminService.confirmBookCreation` with the book ID and stock
2. Checks whether stock < 5
3. Responds with `{ "status": "success" }` in both cases

**Credentials required:**
- **Webhook node:** HTTP Header Auth — configure in n8n and set the same header value as `N8N_API_KEY` in the bookshop app
- **HTTP Request node:** HTTP Basic Auth with username `admin` / password `admin`

---

### `book-deleted.json` — Book Deleted

**Trigger:** CAP fires `POST /webhook/book-deleted` after every book is deleted via `AdminService`.

**Payload received:**
```json
{ "body": { "ID": "...", "title": "...", "author_ID": "..." } }
```

**What it does:**
1. Builds an audit record with a timestamp
2. Calls back into the bookshop's `confirmBookDeletion` action via `POST /odata/v4/admin/AdminService.confirmBookDeletion`
3. Produces a confirmation record with audit details

**Credential required:** The HTTP Request node uses HTTP Basic Auth with username `admin` / password `admin` (as configured in `application.yaml` mock users). Create it in n8n under **Credentials → HTTP Basic Auth** and select it on the `Confirm Deletion via CAP` node.

---

## Authentication

The `book-created-confirmation.json` webhook uses Header Auth. Configure it in n8n and set the matching value in the bookshop app via the `N8N_API_KEY` environment variable:

- **Header Name:** `X-N8N-API-KEY`
- **Header Value:** the same value as `N8N_API_KEY`

See the [Local Development Setup](../README.md#local-development-setup) section in the root README for the full walkthrough.
