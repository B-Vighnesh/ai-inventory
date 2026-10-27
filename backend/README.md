# StockPulse backend

Java 17 / Spring Boot 3.5.7 REST API with Spring Data JPA, validation, and an H2 database in PostgreSQL compatibility mode. All application code is under `com.stockpulse`. Run these commands from `backend/`.

## Run and test

Requires JDK 17 or newer, with `JAVA_HOME` pointing to the JDK (a JRE alone cannot compile). The wrapper downloads Maven 3.9.9; the first build needs internet access.

```sh
cd backend
./mvnw spring-boot:run
# In another terminal, or after stopping the server:
./mvnw clean verify
```

On Windows PowerShell use `.\mvnw.cmd spring-boot:run` and `.\mvnw.cmd clean verify`. The API listens on `http://localhost:8080`.

## Local configuration and Groq

`backend/.env` holds local settings and is ignored by Git. To set up another checkout, copy `.env.example` to `.env`. The application imports `.env` automatically when launched from `backend/` or from the repository root. These files use Java properties syntax: `KEY=value`, without surrounding quotes or `export`. Environment variables supplied by the operating system override values from the file. See [Spring Boot external configuration](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html).

`PORT`, `DB_URL`, `DB_USERNAME`, and `DB_PASSWORD` configure the application. The database still defaults to disposable in-memory H2. A PostgreSQL URL alone is insufficient: its JDBC driver, migrations, and production schema/seed configuration must also be added. Tests disable local env imports and use a separate H2 database.

For Groq, put a key from [Groq API Keys](https://console.groq.com/keys) in `GROQ_API_KEY`. The template sets `GROQ_BASE_URL=https://api.groq.com/openai/v1`, `GROQ_MODEL=openai/gpt-oss-20b`, and `GROQ_TIMEOUT=20s`. The model appears in the published Free plan limits; confirm your organization's availability and exact quotas in [Groq's rate-limit documentation](https://console.groq.com/docs/rate-limits). Request and token quotas vary, and exceeding a limit returns HTTP 429 with `retry-after`.

The backend uses the configured Groq-compatible API when strategy is `AI`; invalid, unavailable, or unconfigured responses fall back to deterministic rules. Keep the key in the backend env file; never include it in frontend configuration or source control. A key shared in chat should be rotated before deployment.

See [INTEGRATION_REVIEW.md](INTEGRATION_REVIEW.md) for the inspected implementation and remaining work.

H2 is in memory: data resets on restart. Hibernate creates the schema and `data.sql` inserts the eight demo products after initialization. Demand uses timestamped orders and a rolling 24-hour total. Recommendations can be generated manually or after configured inventory events; jobs run asynchronously. Initial/manual advice can populate pricing and reorder suggestions. This project is a local demo: configure a persistent database and access control before shared deployment.

CORS allows `http://localhost:5173` and `http://localhost:4200`. Money uses decimal values with at most two fractional digits. New products derive status from stock and default demand velocity to zero. An order body of `{}` defaults quantity to one. Demand velocity is an accumulated counter in this release; a rolling 24-hour calculation requires future order-history or scheduled processing.

## Endpoint examples

These examples use a POSIX shell. On Windows use Git Bash/WSL, or invoke `curl.exe` with JSON quoting appropriate to your PowerShell version.

Create a product (201; IDs and SKUs must be unique):

```sh
curl -i -X POST http://localhost:8080/products -H 'Content-Type: application/json' \
  -d '{"id":"PRD-009","sku":"SKU-HOME-003","name":"Storage Basket","category":"HOME","currentPrice":19.99,"stockLevel":30,"reorderThreshold":10}'
```

List products (each filter is optional):

```sh
curl 'http://localhost:8080/products?status=ACTIVE&category=ELECTRONICS'
```

Get a product:

```sh
curl http://localhost:8080/products/PRD-001
```

Set stock (zero marks OUT_OF_STOCK; restocking an OUT_OF_STOCK product marks ACTIVE):

```sh
curl -X PATCH http://localhost:8080/products/PRD-001/stock -H 'Content-Type: application/json' -d '{"stockLevel":50}'
```

Place an order (positive quantity, default one):

```sh
curl -X POST http://localhost:8080/products/PRD-001/orders -H 'Content-Type: application/json' -d '{"quantity":2}'
```

List pricing suggestions (each filter is optional):

```sh
curl 'http://localhost:8080/pricing-suggestions?status=PENDING&productId=PRD-003'
```

List reorder suggestions (each filter is optional):

```sh
curl 'http://localhost:8080/reorder-suggestions?status=PENDING&productId=PRD-006'
```

Resolve a pricing suggestion (replace the UUID with an existing suggestion ID; action is ACCEPT or REJECT):

```sh
curl -X PATCH http://localhost:8080/pricing-suggestions/EXISTING-UUID -H 'Content-Type: application/json' -d '{"action":"ACCEPT"}'
```

Resolve a reorder suggestion:

```sh
curl -X PATCH http://localhost:8080/reorder-suggestions/EXISTING-UUID -H 'Content-Type: application/json' -d '{"action":"REJECT"}'
```

Resolution examples return 404 until a suggestion exists. Resolving a non-PENDING suggestion returns 409. Pricing acceptance applies the recommended price; reorder acceptance simulates inbound stock. Pricing resolution clears PRICE_REVIEW_PENDING only after the last pending pricing suggestion is resolved. Reorder rejection also clears stale PRICE_REVIEW_PENDING if there are no pending pricing suggestions. Rejection otherwise leaves product data unchanged.

## Commerce recommendations and history

```text
GET   /settings/commerce
PATCH /settings/commerce                       {"strategy":"AI","demandSpikeMultiplier":3}
POST  /products/{id}/review                    generate pricing + reorder advice (202)
POST  /products/{id}/suggest-pricing           pricing advice job (202)
POST  /products/{id}/suggest-reorder            reorder advice job (202)
GET   /recommendation-jobs                     recent asynchronous jobs
GET   /recommendation-jobs/{jobId}             poll one job
POST  /products/{id}/suggest-pricing/stream    pricing advice as SSE
GET   /products/{id}/history                   price and inventory history
```

Stock/order changes can enqueue low-stock or demand-spike reviews after commit. `COMMERCE_EVENTS_ENABLED` and `INITIAL_GENERATION` control automatic triggers. `COMMERCE_STRATEGY` is `AI` or `RULES`; runtime updates use the settings endpoint. The recommendation job queue is in-memory, so queued work does not survive a process restart. Recommendations are advisory; accepting a pricing suggestion is still explicit and rejects stale prices.

## Structure and behavior

- `domain/`: product and suggestion entities; supplier fields are Sprint 2 placeholders.
- `enums/`: shared category, status, direction, trigger, and resolution action enums.
- `repository/`: JPA repositories and a pessimistic product lock to serialize stock updates and suggestion resolution per product.
- `service/`: transactional business rules; each suggestion resolution is one transaction.
- `api/` and `api/dto/`: thin controllers, record DTOs, and JSON error handling (400 validation, 404 missing resources, 409 conflicts).
- `events/`: stock changes are dispatched after commit to the commerce recommendation handler.
- `ai/` and `commerce/`: Groq client, advisor strategies, validation, queue, job lifecycle, and persistence.
- `prompts/`: bounded prompts used by Groq advisor.
- `domain/`: inventory snapshots, price changes, and sale history support historical context.
- `config/`: frontend CORS configuration.
- `src/test/`: MockMvc integration tests for order updates/conflicts, suggestion resolution, stock status, validation, filtering, CORS, and event publication.

Example error response:

```json
{"timestamp":"2026-10-27T12:00:00Z","status":409,"message":"Quantity exceeds available stock","details":[]}
```
