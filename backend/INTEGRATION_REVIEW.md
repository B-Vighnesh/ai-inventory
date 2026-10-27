# Backend integration review

Inspected 27 October 2026: all backend application sources, Maven configuration and wrapper settings, resources, integration tests, and the repository layout. `backend/` is the application; no frontend or deployment configuration is present in the repository. This review describes remaining integrations; it does not implement the future AI feature.

## Implemented

- Product and suggestion entities, shared enums, JPA repositories, DTO records, validation, JSON errors, and frontend CORS origins.
- Product creation, reads/filtering, stock changes, and orders. Pessimistic product locks serialize inventory updates and suggestion resolutions.
- Pricing/reorder suggestion reads/filtering and transactional accept/reject behavior.
- Eight seeded products and H2 in PostgreSQL compatibility mode.
- Stock-change publication and an AFTER_COMMIT listener. The listener uses `ObjectProvider<StockChangeHandler>`; no handler bean is implemented.
- Local env loading and reserved Groq configuration, added during this review. The local key is kept in the ignored `.env`; `.env.example` contains no key.

## Remaining for the Groq suggestion flow

| Priority | Integration | Current evidence and required work |
| --- | --- | --- |
| 1 | Groq client | No HTTP/SDK client exists in `config/` or `service/`. Consume `stockpulse.groq.*`, send authenticated requests, bound timeouts/output size, and validate typed JSON responses before persistence. A configured key is not an integration. |
| 1 | Suggestion generation | No production code creates suggestion records. Add pricing/reorder generation services with price, quantity, confidence, and lead-time validation. Use current database state, apply business bounds, save PENDING records, and set PRICE_REVIEW_PENDING where appropriate. Keep acceptance under the existing explicit user action. |
| 1 | Event handler and reliable execution | Implement `StockChangeHandler` to evaluate low inventory/demand changes. Debounce repeated updates, avoid duplicate pending suggestions, handle stale results, and persist in a new transaction after commit. Keep slow provider work off the request thread; use retryable work with failure tracking if delivery must survive restarts. |
| 1 | Initial/manual triggers | `TriggerReason` declares INITIAL and MANUAL, but no startup/new-product generation or manual generation route exists. Decide and implement when initial suggestions are generated and how users request regeneration. |
| 1 | Free-tier handling | Add request/token budgeting, bounded retries honoring 429 `retry-after`, and a failure state that leaves orders working. Do not call the provider for every stock event without controlling volume. |
| 2 | End-to-end tests | Current tests check stock event publication and API rules, but do not verify a registered handler, committed-vs-rolled-back delivery, or a Groq flow. Add mocked provider tests for valid/invalid responses, timeouts, 429s, deduplication, and persisted suggestions. |

Groq documents the compatible base URL as `https://api.groq.com/openai/v1`. `openai/gpt-oss-20b` appears in its Free plan rate-limit table; the env template uses it as a configurable starting point. Exact account availability and quotas must be checked in the console. No live Groq request or key validation was performed during this review.

## Functional and deployment work

| Priority | Area | Remaining work |
| --- | --- | --- |
| 1 for accurate demand | 24-hour demand | `ProductService.order()` increments `demandVelocity` indefinitely. There is no timestamped order history or expiry calculation. Store orders and compute the rolling 24-hour total before using it to infer demand spikes. |
| 1 before durable deployment | Database | `ddl-auto=create-drop` and in-memory H2 discard data. Add the PostgreSQL JDBC driver, migrations (for example Flyway), production configuration using schema validation, and separate development seeding. Do not reuse `data.sql` initialization on every production startup. |
| Before shared deployment | Access control | No authentication, authorization, or tenant ownership exists. CORS controls browser origins, not who can modify inventory or accept suggestions. Add access rules appropriate to intended users. |
| Before deployment | Operations | No container/deployment setup, health endpoints, CI workflow, or provider-call monitoring exists. Add these when selecting a hosting environment. |
| As data grows | API scale | List endpoints return all matching records. Add pagination and indexes based on production queries and expected volume. |
| Sprint 2 | Suppliers and real inbound stock | `costPrice` and `supplierId` remain placeholders. Reorder acceptance adds stock immediately; supplier APIs, purchase orders, and delivery confirmation are not implemented. |
| When connecting a UI | Frontend | Backend routes and CORS are ready for a local frontend. The UI client, error/loading states, and refresh after suggestion resolution remain external integrations. |

## Existing behavior to account for

- The seed product PRD-003 is PRICE_REVIEW_PENDING even though suggestion tables start empty. Either generate initial suggestions or adjust demo seeding when that flow is introduced.
- `StockChangedListener` invokes handlers synchronously after commit. A future slow or throwing handler can delay or fail an HTTP response after the stock transaction has already committed. New handler writes need their own transaction; isolate provider failures from the order response.
- Stock events are emitted for PATCH and orders only. Product creation and accepted reorders do not trigger generation. Decide whether those operations should enter the future loop, with loop prevention.
- Suggestions have no expiry/version snapshot. Before accepting generated advice, define whether it should be rejected or refreshed when the stock/price context has changed.

## Validation during this review

- All 23 existing integration tests passed with the isolated test database.
- Packaging in `backend/target` encountered a Windows file lock on the existing JAR. An identical source copy under the ignored `.tools/env-review-backend` directory packaged successfully without stopping running processes.
- The packaged application started successfully in non-web mode from the repository root; configuration trace confirmed that `backend/.env` was imported. No Groq request was made.
- Git ignores the local `.env`, the example key is blank, and no env file is included in the application JAR.

## Sources

- [Groq overview and API base URL](https://console.groq.com/docs/overview)
- [Groq supported models](https://console.groq.com/docs/models)
- [Groq rate limits and HTTP 429 behavior](https://console.groq.com/docs/rate-limits)
- [Spring Boot external configuration and extension hints](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html)
