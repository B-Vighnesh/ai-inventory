# StockPulse backend

Java 17 / Spring Boot 3.5.7 REST API with Spring Data JPA, validation, and an H2 database in PostgreSQL compatibility mode. All application code is under `com.stockpulse`. The original project in the parent folder is separate; run these commands from `backend/`.

## Run and test

Requires JDK 17 or newer, with `JAVA_HOME` pointing to the JDK (a JRE alone cannot compile). The wrapper downloads Maven 3.9.9; the first build needs internet access.

```sh
cd backend
./mvnw spring-boot:run
# In another terminal, or after stopping the server:
./mvnw clean verify
```

On Windows PowerShell use `.\mvnw.cmd spring-boot:run` and `.\mvnw.cmd clean verify`. The API listens on `http://localhost:8080`.

H2 is in memory: data resets on restart. Hibernate creates the schema and `data.sql` inserts the eight requested products after initialization. Suggestion tables start empty. Suggestion generation and creation endpoints are intentionally outside this scope; integration tests create fixtures through repositories. No AI or strategy implementation is included.

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

## Structure and behavior

- `domain/`: product and suggestion entities and enums; supplier fields are Sprint 2 placeholders.
- `repository/`: JPA repositories and a pessimistic product lock to serialize stock updates and suggestion resolution per product.
- `service/`: transactional business rules; each suggestion resolution is one transaction.
- `api/` and `api/dto/`: thin controllers, record DTOs, and JSON error handling (400 validation, 404 missing resources, 409 conflicts).
- `events/`: `StockChangedEvent` is published after saving stock PATCH and order changes, within the service transaction. The future listener should use `@TransactionalEventListener` to act only after commit; no listener is implemented.
- `config/`: frontend CORS configuration.
- `src/test/`: MockMvc integration tests for order updates/conflicts, suggestion resolution, stock status, validation, filtering, CORS, and event publication.

Example error response:

```json
{"timestamp":"2026-10-27T12:00:00Z","status":409,"message":"Quantity exceeds available stock","details":[]}
```
