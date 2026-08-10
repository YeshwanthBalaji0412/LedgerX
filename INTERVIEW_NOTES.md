# LedgerX — Interview Notes

## Domain one-liners (from day 0 briefing)
- A double-entry ledger records money movement as balanced debit/credit pairs; balances are derived, never stored as editable state.
- The killer property is a machine-checkable invariant: every transaction sums to zero, so corruption is detectable and every balance is provable from primary records.
- A `balance` column is lossy (no history) and unverifiable (no invariant) — that's why Stripe, Adyen, Uber, and every core banking platform run entry-based ledgers.
- My stack (Java, Spring Boot, Postgres, Kafka, Redis) is the industry-standard ledger stack — Adyen is the existence proof at global scale.
- TigerBeetle exists for million-TPS accounting workloads; most companies still choose Postgres because the ledger must share ACID transactions with adjacent data and their volume is far below Postgres's ceiling. I can articulate when I'd switch.
- The three money bugs my design defends against: lost updates (optimistic locking), double spends on retry (idempotency keys), and unauditable drift (immutable entries + Kafka audit trail).
- Honest scope limits vs. the real thing: no external reconciliation against bank files, single currency, no KYC/AML, no multi-region durability.

## Setup decisions (Day 0)
- Flyway owns the schema; Hibernate set to `validate` only — schema changes are versioned, reviewable migrations, like production teams do.
- Disabled open-in-view: avoids hidden lazy-loading queries during response rendering.
- Kafka runs in KRaft mode — ZooKeeper is gone from modern Kafka.
- pgvector image from day one so the RAG milestone needs no infra change.
- Spring Initializr no longer offers Spring Boot 3 — project runs on Boot 4.1.0. Verified this is a refinement, not a rewrite: Java baseline unchanged at 17, main visible change is starter modularization (spring-boot-starter-web -> spring-boot-starter-webmvc, spring-kafka -> spring-boot-starter-kafka, flyway-core -> spring-boot-starter-flyway + flyway-database-postgresql). Jackson 3 is now default — worth watching for money/BigDecimal serialization behavior once the ledger API ships.
- Testcontainers pinned to match Compose exactly (pgvector/pgvector:pg16, redis:7-alpine, apache/kafka:3.8.0) instead of :latest, so test DB has the same extensions as the real one and tests stay reproducible.

## Gaps closed
- Confirmed Boot 4 doesn't change the Java 21 decision or any other locked stack choice.

## Gaps to close
- (log anything that confuses you here as we build)

## Additional Day 0 decisions
- Pinned JAVA_HOME to Temurin 21 explicitly via shell profile, even though a newer JDK (25) was also installed — real teams pin JDK versions per project rather than relying on whatever the machine defaults to.
- Chose a monorepo (backend/ + frontend/ in one repo) over two separate repos — simpler to showcase as one coherent project, one README, one CI pipeline, at the cost of slightly coupled deploy pipelines later.
- CI's frontend job currently skips `npm run lint` deliberately — a fresh Vite scaffold's default lint config isn't meaningful until real code exists to lint against; will add back once the frontend has actual components.

## Day 0 continued — frontend styling stack
- Vite's react-ts scaffold defaulted to React 19.2.8, not React 18 as originally planned. Confirmed React 19 is stable and fully supported by every other planned library (TanStack Query, RHF, Zod, Router, shadcn/ui, Recharts) — no downgrade needed, locked stack updated to reflect reality.
- Adopted Tailwind CSS v4 (not v3) — the `@tailwindcss/vite` plugin replaces the old PostCSS-based setup, and `src/index.css` uses a single `@import "tailwindcss"` instead of the old three `@tailwind` directives.
- shadcn/ui requires a `@/*` path alias resolving to `src/`. On current TypeScript, `baseUrl` is deprecated as a hard error under `moduleResolution: "bundler"` — `paths` alone resolves correctly without it. shadcn's own docs still show the older baseUrl+paths pattern, which breaks on current TS — had to diagnose and fix this directly.
- shadcn is listed in `dependencies`, not `devDependencies` — correct despite looking unusual for a CLI tool, because `index.css` does a runtime `@import` of shadcn's base styles, making it a genuine runtime CSS dependency.
