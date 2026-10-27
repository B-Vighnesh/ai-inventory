# StockPulse frontend

React 18 and Vite console connected to the Spring Boot API in `../backend`.

## Run locally

Start the backend on port 8080 using `../backend/README.md`. Then, from this folder:

```sh
npm ci
cp .env.example .env
npm run dev
```

In PowerShell, use `Copy-Item .env.example .env` and `npm.cmd` if script execution policy blocks `npm`.
Open **http://localhost:5173**. Vite uses a strict port compatible with backend CORS.

The local `.env` uses:

```properties
VITE_API_URL=/api
BACKEND_URL=http://localhost:8080
VITE_DEMO_MODE=false
```

The browser calls `/api/products`, `/api/pricing-suggestions`, etc. Vite proxies requests to Spring Boot and removes `/api`. Change `BACKEND_URL` if the backend uses another port, then restart Vite. `npm run preview` uses the same local proxy.

For deployment, configure a reverse proxy for `/api` to the backend, or set `VITE_API_URL` to the deployed backend URL before building and allow the deployed frontend origin in backend CORS. Vite configuration is baked into the browser bundle at build time. Keep Groq keys only in `backend/.env`; frontend `VITE_` variables are public.

## Live behavior

- Inventory and pending suggestions load from the backend and refresh every four seconds.
- Sales and stock edits save to the backend. Accept/reject refreshes inventory and both suggestion lists.
- Backend validation/conflict messages appear in notifications. Conflicts trigger a refresh to fetch the current state.
- Failed loads show a disconnected state with Retry and automatic recovery. Previously loaded data stays visible with an error notice; changes are disabled until connected again. Failures never switch to fake data.
- Timeouts do not automatically retry writes, since the backend may already have saved them.
- Manual advisor requests and automatic suggestion generation are not implemented in the current backend. Live mode hides the advisor action and does not claim an agent is processing inventory changes. Empty suggestion lists are expected until backend generation is implemented. Existing suggestions can be approved or rejected.
- Demand count displays the backend counter; the backend does not yet calculate a rolling 24-hour window.

## Explicit demo mode

Set `VITE_DEMO_MODE=true` in this folder's `.env` and restart Vite to use the existing in-memory simulation. The header and banner label this mode. Demo changes never reach the backend. Set it back to `false` and restart to return to live inventory.

## Endpoints used in live mode

| Action | Endpoint |
| --- | --- |
| Inventory | `GET /products` |
| Pending recommendations | `GET /pricing-suggestions?status=PENDING`, `GET /reorder-suggestions?status=PENDING` |
| Sale | `POST /products/{id}/orders` with `{ "quantity": n }` |
| Stock edit | `PATCH /products/{id}/stock` with `{ "stockLevel": n }` |
| Approve/reject | `PATCH /pricing-suggestions/{id}` or `/reorder-suggestions/{id}` with `{ "action": "ACCEPT" }` or `"REJECT"` |

## Checks

```sh
npm test
npm run build
```

API client tests cover the backend contract, failures, recovery, validation messages, and mutation timeouts. The build checks the React/Vite bundle. Activity shown in the console is scoped to the current browser session.
