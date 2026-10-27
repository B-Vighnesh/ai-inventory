# StockPulse quick start

## Start the backend

From the repository root, open a terminal and run:

```powershell
Set-Location backend
.\mvnw.cmd spring-boot:run
```

The API starts at `http://localhost:8080`. It uses disposable in-memory H2 by default and seeds eight demo products. For Groq-backed recommendations, copy `backend/.env.example` to `backend/.env` and set `GROQ_API_KEY` there. The advisor falls back to rules when no provider key is configured or a provider response is unavailable.

## Start the frontend

In another terminal:

```powershell
Set-Location frontend
npm.cmd ci
npm.cmd run dev
```

Open `http://localhost:5173`. Vite proxies `/api` to the backend. The inventory console supports sales and stock changes, AI or rules strategy, manual recommendation review, job tracking, pricing/inventory history, and suggestion approval.

## Verify

From the repository root:

```powershell
Set-Location backend
.\mvnw.cmd test
Set-Location ..\frontend
npm.cmd test
npm.cmd run build
```

Backend endpoint reference and configuration are in [backend/README.md](backend/README.md). Known production gaps (durable database, authentication, deployment setup, and real supplier ordering) are listed in [backend/INTEGRATION_REVIEW.md](backend/INTEGRATION_REVIEW.md).
