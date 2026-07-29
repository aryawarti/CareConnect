# Non-Functional Requirements

Targets are sized for a realistic small deployment, and each is verifiable in this project — no aspirational numbers we can't demonstrate.

| ID | Category | Requirement | How it's addressed / verified |
|---|---|---|---|
| NFR-1 | Security | All traffic enters via the gateway; JWT validated at the edge; service-level role checks; passwords BCrypt-hashed; secrets via env/config, never committed | Security config + tests; `docs/architecture/security.md` |
| NFR-2 | Availability | Failure of a non-critical service (notification, billing) must not break appointment booking | Async events decouple them; circuit breakers on sync calls |
| NFR-3 | Performance | p95 < 500 ms for standard reads locally; pagination mandatory on all list endpoints | Actuator metrics; no unbounded queries |
| NFR-4 | Consistency | Cross-service data uses eventual consistency via events; each service is the single writer of its own data | ADR-003, ADR-004; idempotent consumers |
| NFR-5 | Observability | Structured JSON logs with correlation IDs propagated across services; health/metrics endpoints on every service | `docs/operations/observability.md` |
| NFR-6 | Maintainability | Identical module layout across services; DTO boundaries; standard error format; migrations for every schema change | Coding standards; PR review checklist |
| NFR-7 | Testability | Unit tests for domain logic, slice tests for web/JPA, Testcontainers integration tests per service | `docs/engineering/testing-strategy.md` |
| NFR-8 | Portability | Entire system runs with `docker compose up` on a laptop | Compose file kept current every phase |
| NFR-9 | Data protection | Medical data role-restricted; audit fields (created/updated by/at) on all entities; no PII in logs | JPA auditing; logging guidelines |
