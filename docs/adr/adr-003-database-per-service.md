# ADR-003: Database per service

**Status:** Accepted · 2026-07-18

## Context
Microservices sharing one database are a distributed monolith: schema changes ripple everywhere and boundaries exist only on paper.

## Decision
Each service owns a logical database no other service may touch. Locally: one Postgres container, one database per service (created by `infra/postgres/init`). Cross-context references are UUIDs; cross-context reads happen via API or event-carried snapshots.

## Alternatives
- **Shared database** — enables joins and ACID across contexts, but couples every schema change and makes "single writer" unenforceable. The most common real-world microservice failure mode; rejected.
- **Schema-per-service in one DB** — same isolation guarantees in practice with lighter ops; a fine variant. Chose database-per-service as the cleaner statement of intent; the local cost is identical (still one container).
- **Polyglot persistence** (e.g., Mongo for records) — no requirement demands a second database technology; adding one would be résumé-driven design.

## Consequences
+ Boundaries physically enforced; migrations independent; per-service data model freedom.
− No cross-service joins → some data duplicated by design (fee/name snapshots); consistency is eventual and must be reasoned about explicitly (see communication.md).
