# LedgerX

![CI](https://github.com/YeshwanthBalaji0412/LedgerX/actions/workflows/ci.yml/badge.svg)
![License: MIT](https://img.shields.io/badge/license-MIT-blue.svg)

A double-entry payments ledger platform — built to demonstrate real financial
system design, not CRUD: balanced debit/credit entries, an idempotent transfer
lifecycle, optimistic concurrency control to prevent overdraw, and an
immutable Kafka-backed audit trail.

This is a portfolio project built end-to-end with a production-representative
stack, aimed at backend/fintech engineering roles. See
[`INTERVIEW_NOTES.md`](./INTERVIEW_NOTES.md) for the domain background and
the reasoning behind every architectural decision, logged as they were made
rather than written up after the fact.

## Why a ledger?

A double-entry ledger records every movement of money as balanced
debit/credit pairs rather than an editable balance column. That gives the
system a machine-checkable invariant — every transaction sums to zero — which
is exactly how real payment processors, fintechs, and core banking platforms
guarantee correctness and auditability. Full writeup in `INTERVIEW_NOTES.md`.

## Stack

**Backend:** Java 21, Spring Boot 4.1.0, Spring Data JPA/Hibernate, Spring
Security (JWT, refresh rotation, RBAC), Kafka, Redis, PostgreSQL + Flyway,
springdoc-openapi, Spring AI (RAG — planned)

**Frontend:** React 19 + TypeScript (Vite 8), TanStack Query v5, React Hook
Form + Zod 4, React Router 7, Tailwind CSS v4 + shadcn/ui, Recharts

**Testing:** JUnit 6, Mockito, AssertJ, Testcontainers (real
Postgres/Redis/Kafka in tests), Vitest + React Testing Library, Playwright
(planned)

**Infra:** Docker Compose (local dev), GitHub Actions CI, AWS deployment
(planned), Spring Actuator

> Note: Spring Boot 3 is no longer offered by Spring Initializr as of this
> writing, so the backend runs on Boot 4.1.0. This is a refinement, not a
> rewrite — Java baseline is unchanged, and the practical difference is
> mostly starter-module naming. Details in `INTERVIEW_NOTES.md`.
> Vite's React scaffold now defaults to React 19, not 18 as originally
> planned. Fully compatible with every library in this stack.

## Project status

**Core platform complete and tested end to end.** 234 tests: 113 backend
(JUnit + Testcontainers against real Postgres, Redis and Kafka) and 121
frontend (Vitest + React Testing Library).

- [x] Docker Compose infra: Postgres (with pgvector), Redis, Kafka (KRaft mode)
- [x] Auth: JWT, single-use refresh rotation with reuse detection, RBAC
- [x] Ledger core: accounts, balanced debit/credit entries, derived balances,
      append-only enforced by database triggers
- [x] Transfer lifecycle: claim-first idempotency, optimistic locking,
      rate limiting, settlement
- [x] Transactional outbox → Kafka → immutable audit trail
- [x] Fraud velocity flags with an admin review queue
- [x] Monthly statements derived from ledger entries, immutable once issued
- [x] Observability: custom metrics, outbox-lag health indicator, OpenAPI docs
- [x] Demo seeding through the real service path, gated by the integrity check
- [x] Frontend: protected routes, dashboard, transfers with optimistic updates
      and rollback, statements, admin fraud queue and audit log
- [x] GitHub Actions CI: backend verify; frontend lint, test, build, bundle check
- [ ] RAG assistant (Spring AI + pgvector), scoped per user
- [ ] Playwright E2E, coverage gate, AWS deployment config

## Running locally

Requires Docker Desktop, Java 21, Node 20+.

```bash
# 1. Start infra
docker compose up -d

# 2. Run the backend
cd backend
./mvnw spring-boot:run
# health check: curl localhost:8080/actuator/health

# 3. Run the frontend (separate terminal)
cd frontend
npm install
npm run dev
```

Vite proxies `/api` to `localhost:8080`, so the app is same-origin in
development and no environment file is needed. Setting `VITE_API_BASE_URL`
points the client straight at the backend instead, which is how the CORS
configuration gets exercised deliberately rather than discovered on a
split-origin deployment.

## Tests

```bash
cd backend && ./mvnw verify      # 113 tests; starts real Postgres/Redis/Kafka containers
cd frontend && npm run test      # 121 tests
cd frontend && npm run check:bundle   # after npm run build
```

Backend tests run against real containers rather than an in-memory database,
because most of what is worth testing here — append-only triggers, unique
constraints under concurrency, transaction rollback semantics — is behaviour
Postgres provides and a substitute does not.

`check:bundle` inspects the built output and fails if Recharts ends up in the
entry chunk. Bundle shape is invisible to unit tests: replacing the lazy chart
import with a static one keeps every test green and only makes the download
worse.

## Demo data

Start the backend with the `seed` profile and every feature has something to
show — three closed months of history plus current-month activity, statements,
a populated audit trail, and a fraud queue with one flag in each state:

```bash
docker compose up -d
cd backend
./mvnw spring-boot:run -Dspring-boot.run.profiles=seed
```

| Sign in as | Password | |
|---|---|---|
| `alice@ledgerx.dev` | `demo-password-123` | demo user with history |
| `bob@ledgerx.dev` | `demo-password-123` | the other side of those transfers |
| `admin@ledgerx.dev` | `demo-password-123` | ADMIN: fraud queue, integrity check, metrics |

Seeding is opt-in, so it never runs in tests or in an environment that does not
ask for it, and re-running it is a no-op rather than a second helping of
history. Every seeded movement goes through the same balanced-entry path the
API uses — nothing inserts ledger rows directly — and the seeder runs the
integrity check at the end, refusing to finish starting if the books do not
balance.

## API documentation

With the app running, Swagger UI is at
[localhost:8080/swagger-ui/index.html](http://localhost:8080/swagger-ui/index.html)
and the raw document at `/v3/api-docs`. Sign in through `POST /api/auth/login`,
paste the access token into **Authorize**, and every endpoint becomes callable
from the page.

Both paths are open without a token because they serve only the API
*description* — paths, schemas and status codes, no data and no way to invoke
anything. Everything they document still requires a token. To close them in an
environment that should not expose them, set `springdoc.api-docs.enabled=false`
and `springdoc.swagger-ui.enabled=false`; the endpoints then stop existing, so
the security rules and the exposure decision cannot drift apart.

## Observability

`/actuator/health` and `/actuator/info` are public; `/actuator/metrics`
requires ADMIN, since metrics describe internal behaviour and volumes.

Beyond the defaults, these are the counters worth watching, all covering things
that are invisible from the outside because they succeeded or were absorbed:

| Metric | What it tells you |
|---|---|
| `ledgerx.transfers{status}` | Volume by lifecycle state; a rise in FLAGGED or FAILED stands alone |
| `ledgerx.transfer.optimistic.retries` | Contention on account balances, before it becomes a 409 |
| `ledgerx.idempotency.replays` | Retries that cost nothing, which is the feature working |
| `ledgerx.transfer.ratelimit.rejections` | Callers hitting the window |
| `ledgerx.fraud.flags{rule}` | Flags raised, split by rule |
| `ledgerx.outbox.pending` | Unpublished events: the backlog gauge |

Health includes an `outbox` indicator that goes DOWN past
`ledgerx.outbox.lag-threshold` (default 250). Outbox lag is the failure that is
invisible everywhere else: writes keep committing and the API stays green while
events quietly stop reaching Kafka, leaving the audit trail and fraud detection
running blind.

## Scope limits

This is a portfolio system, not a payment processor. The limits below are
deliberate and stated plainly rather than left for a reader to discover.

- **There is no external funding source, so any authenticated user can mint
  themselves unlimited funds.** `POST /api/accounts/{id}/deposits` credits an
  account against the treasury with no upstream card, bank, or settlement rail
  behind it. A per-movement ceiling and a per-user rate limit bound how fast
  that happens, not how much: at the configured limits that is roughly $60M per
  minute, indefinitely. This is a sandbox faucet, and it is the single largest
  gap between this and a real system.
- **No server-side state change takes effect until an access token expires.**
  Logout, user deletion, a role change, and an account freeze all leave an
  already-issued access token valid for the rest of its five minute life. That
  is the cost of stateless verification, taken knowingly.
- **Authentication endpoints are not rate limited.** Rate limiting covers
  transfers, not registration or login, so credential stuffing is not defended
  against.
- Single currency (USD), no reconciliation against external bank files, no
  KYC/AML, no multi-region durability, and no dead letter queue: an event that
  cannot be published is retried indefinitely rather than parked.

## Database roles

The application connects as `ledgerx_app`, a role that can read and write rows
but cannot alter schema — so a compromised application cannot disable the
append-only triggers on `ledger_entries` and `audit_log`. Migrations run
separately as the schema owner `ledgerx`, because DDL is exactly the privilege
the runtime must not hold.

`docker/postgres/init/01-app-role.sql` runs automatically on a fresh volume. To
apply it to a database that already exists:

```bash
docker compose exec -T postgres psql -U ledgerx -d ledgerx \
  < docker/postgres/init/01-app-role.sql
```

## Repository structure

```
LedgerX/
├── .github/workflows/ci.yml     # Backend verify; frontend lint, test, build, bundle check
├── backend/                     # Spring Boot API (Java 21, Boot 4.1.0)
│   └── src/
│       ├── main/
│       │   ├── java/dev/ledgerx/
│       │   │   ├── api/         # Cross-cutting HTTP concerns: one error shape, OpenAPI config
│       │   │   ├── audit/       # Outbox publisher, Kafka consumer, append-only audit trail
│       │   │   ├── auth/        # JWT, refresh rotation with reuse detection, security config
│       │   │   ├── fraud/       # Velocity rules, flags, admin review
│       │   │   ├── ledger/      # Accounts, balanced entries, derived balances, integrity check
│       │   │   ├── seed/        # Demo data, opt-in by profile, through the real service path
│       │   │   ├── statement/   # Monthly statements, immutable once issued
│       │   │   └── transfer/    # Transfer lifecycle, idempotency, settlement, rate limiting
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/    # Flyway V1–V9; schema is owned here, never by Hibernate
│       └── test/java/dev/ledgerx/   # Integration tests on real containers, mirroring the packages
├── frontend/                    # React 19 + TypeScript (Vite 8)
│   ├── scripts/check-bundle.mjs # Fails the build if Recharts creeps into the entry chunk
│   └── src/
│       ├── auth/                # Session provider, route guards
│       ├── components/          # Layout, error boundary, shared pieces, shadcn/ui primitives
│       ├── lib/api/             # Typed client, single-flight refresh, queries and mutations
│       ├── routes/              # dashboard, transfers, statements, admin, auth
│       └── test/                # Render helpers and setup
├── docker/postgres/init/        # Least-privilege app role, applied on a fresh volume
├── docker-compose.yml           # Postgres (pgvector), Redis, Kafka (KRaft)
├── INTERVIEW_NOTES.md           # Architecture decisions, logged as they were made
├── LICENSE
└── README.md
```