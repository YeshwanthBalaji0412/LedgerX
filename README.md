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
Spring AI (RAG, planned)

**Frontend:** React 19 + TypeScript (Vite), TanStack Query, React Hook Form +
Zod, React Router, Tailwind CSS + shadcn/ui, Recharts (planned)

**Testing:** JUnit 5, Mockito, Testcontainers (real Postgres/Redis/Kafka in
tests), React Testing Library, Playwright (planned)

**Infra:** Docker Compose (local dev), GitHub Actions CI, AWS deployment
(planned), Spring Actuator

> Note: Spring Boot 3 is no longer offered by Spring Initializr as of this
> writing, so the backend runs on Boot 4.1.0. This is a refinement, not a
> rewrite — Java baseline is unchanged, and the practical difference is
> mostly starter-module naming. Details in `INTERVIEW_NOTES.md`.
> Vite's React scaffold now defaults to React 19, not 18 as originally
> planned. Fully compatible with every library in this stack.

## Project status

🚧 **Early build — environment and scaffolding complete, core features not
yet implemented.**

- [x] Docker Compose infra: Postgres (with pgvector), Redis, Kafka (KRaft mode)
- [x] Spring Boot backend boots, connects to Postgres, Flyway migrations apply
- [x] Vite + React + TypeScript frontend scaffold
- [x] GitHub Actions CI (backend build+test, frontend build)
- [ ] Auth (JWT, refresh rotation, roles) — next up
- [ ] Ledger core (accounts, balanced entries, derived balances)
- [ ] Transfer lifecycle with idempotency + optimistic locking
- [ ] Kafka outbox + audit trail
- [ ] Frontend: protected routes, transfer UI with optimistic updates
- [ ] Fraud velocity flags, statements, Recharts, RAG assistant (trim-layer,
      built after the core is complete)

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

## Repository structure

```
LedgerX/
├── .github/
│   └── workflows/
│       └── ci.yml              # Backend build+test, frontend build
├── backend/                    # Spring Boot API (Java 21, Boot 4.1.0)
│   ├── .mvn/wrapper/
│   ├── mvnw / mvnw.cmd
│   ├── pom.xml
│   └── src/
│       ├── main/
│       │   ├── java/dev/ledgerx/
│       │   │   └── LedgerxApplication.java
│       │   └── resources/
│       │       ├── application.yml
│       │       └── db/migration/
│       │           └── V1__init.sql   # Flyway: schema_sanity check table
│       └── test/
│           └── java/dev/ledgerx/
│               ├── LedgerxApplicationTests.java
│               └── TestcontainersConfiguration.java  # Postgres/Redis/Kafka test containers
├── frontend/                   # React + TypeScript (Vite)
│   ├── public/
│   ├── src/
│   │   ├── App.tsx
│   │   ├── main.tsx
│   │   └── index.css
│   ├── components.json         # shadcn/ui config
│   ├── index.html
│   ├── package.json
│   ├── tsconfig.json
│   └── vite.config.ts
├── docker-compose.yml          # Postgres (pgvector), Redis, Kafka (KRaft)
├── INTERVIEW_NOTES.md          # Architecture decisions, logged day by day
├── LICENSE
├── README.md
└── .gitignore
```