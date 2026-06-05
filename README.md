# Real Estate CxO Decision-Support Agent

An AI agent that helps a real-estate CxO compare a small, user-chosen set of developers (1–4) and make
strategic decisions — collecting the credible structured data that exists (RERA-first), reconciling
conflicting values across sources **with full citation**, filling gaps with user input, and running
what-if scenarios.

## Documentation
- **Architecture:** [`docs/architecture_v2_prudent.md`](docs/architecture_v2_prudent.md) (v2.2 — the agreed design)
- **Decisions:** [`docs/adr/`](docs/adr/) (ADR-0001 … 0004)
- Strategic value & original BRD: [`docs/cxo_strategic_value_analysis.md`](docs/cxo_strategic_value_analysis.md), `BRD - ... .pdf`

## Two rules that anchor everything
1. **The LLM orchestrates and narrates; it never calculates** (ADR-0001).
2. **Never collapse a value silently** — conflicting source values are reconciled, not averaged (ADR-0002).

## Tech stack
Java 25 (LTS) · Spring Boot 3.5 · Spring Modulith · PostgreSQL + pgvector · Flyway · LangChain4j + Gemini (later).

## Prerequisites
- JDK 25
- Maven 3.9+
- Docker (for local Postgres) — or a reachable PostgreSQL with the `pgvector` extension

## Run locally
```bash
# 1. Start Postgres (pgvector)
docker compose up -d

# 2. Run the app (local profile)
./mvnw spring-boot:run -Dspring-boot.run.profiles=local

# 3. Verify
curl http://localhost:8080/api/ping
curl http://localhost:8080/actuator/health
```

## Test
```bash
./mvnw test
```
`ModularityTests` verifies the module boundaries (architecture §3) and writes component diagrams to
`target/spring-modulith-docs/`. It needs no database or Docker.

## Module layout (modular monolith — ADR-0003)
```
com.antigravity.companalysis
├─ shared          (open) common types, provenance/credibility tiers, events
├─ catalog         entities + append-only ParameterObservations + comparison sessions
├─ collection      targeted RERA-first acquisition + comparable-set gathering
├─ userinput       gap-fill, what-if, "our project" anchor
├─ reconciliation  normalize → consolidate (ANCHOR/WEIGHTED/RANGE) → variance
├─ analytics       deterministic comparison engines
├─ intelligence    LangChain4j + Gemini agent (orchestrates/narrates only)
└─ api             REST + SSE
```
Allowed dependencies are declared in each module's `package-info.java` and enforced by the build.

## Status
**Delivery step 2 complete** — catalog domain (sessions, developer entities, append-only observations),
the comparison-session API, and the grid read-model (entities × parameters with credibility status + gap
detection). Next: the **reconciliation engine** (normalize → anchor → variance). See
`docs/architecture_v2_prudent.md` §12 for the delivery order.

### API (step 2)
```
POST /api/comparisons                 { title, microMarket }            -> { id }
GET  /api/comparisons/{id}                                              -> SessionView
POST /api/comparisons/{id}/entities   { developerName, projectName, … } -> { id }   (max 4)
POST /api/comparisons/{id}/observations { entityId, parameter, source, … } -> { id }
GET  /api/comparisons/{id}/grid                                         -> GridResponse
```
Each grid cell reports a status — `CREDIBLE` (has RERA/authoritative), `USER_PROVIDED`, `INDICATIVE`
(advertised only), or `MISSING` — plus the underlying source observations and a list of gaps to fill.

> **Note:** `mvnw`/`mvnw.cmd` wrapper scripts are not yet generated — run `mvn -N wrapper:wrapper` once
> (with Maven installed) to add them, or use a system `mvn`.
