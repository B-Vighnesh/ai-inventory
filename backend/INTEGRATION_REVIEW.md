# Backend integration review

Updated 27 October 2026 after implementation and verification. The connected React/Vite application lives in `frontend/`.

## Implemented

- Product CRUD, stock updates, orders, filters, suggestion resolution, validation, and consistent JSON errors.
- Rolling 24-hour sales demand and persisted sales, inventory snapshots, and price history.
- Rule-based and Groq-backed advisor strategies. Provider output is validated and deterministic rules provide a fallback. Requests use timeouts, response bounds, a free-tier request budget, and 429 cooldown handling.
- Asynchronous recommendation jobs, duplicate suppression, initial/manual/event triggers, and optional POST-based SSE pricing stream.
- Strategy settings, product history, and recommendation review endpoints wired into the frontend.
- After-commit stock handling; product create/initial review support; stale context checks before persistence and before price acceptance.
- Local env import; secrets are ignored. The user-provided key is only stored in the local backend env file and is not part of the source tree.
- Eight seeded products. H2 remains the default development database and is in-memory.
- Backend tests: 43 passed. Frontend API tests and production build passed during implementation.

Groq live credentials/provider behavior were not verified with an external request; the integration is covered with local HTTP-stub tests.

## Remaining before production deployment

| Priority | Area | Remaining work |
| --- | --- | --- |
| Needed for durable deployment | Database | Default is `ddl-auto=create-drop` with in-memory H2. Add PostgreSQL driver, migrations, persistent production configuration, and production-safe seed separation. |
| Needed before shared deployment | Access control | No authentication, authorization, or tenant ownership. CORS is not access control. |
| Needed before deployment | Operations | No deployment/container config, CI workflow, or provider-call monitoring. Choose a host and add health/metrics/secret configuration. |
| As data grows | API scale | List endpoints are not paginated. Add pagination/indexes for production workloads. |
| Later scope | Suppliers and inbound stock | Supplier fields remain placeholders. Accepted reorder suggestions simulate inbound stock; purchase orders and delivery confirmation are not implemented. |

## Validation

- Backend Maven suite: 43 tests passed.
- Frontend Node test suite and Vite production build passed.
- No live Groq request was made. The provider key shared in chat should be rotated in the Groq console before any deployment.

## Sources

- [Groq overview and API base URL](https://console.groq.com/docs/overview)
- [Groq supported models](https://console.groq.com/docs/models)
- [Groq rate limits and HTTP 429 behavior](https://console.groq.com/docs/rate-limits)
- [Spring Boot external configuration and extension hints](https://docs.spring.io/spring-boot/3.5/reference/features/external-config.html)
