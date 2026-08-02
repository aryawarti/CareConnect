# Architecture Decision Records

One file per decision. Accepted ADRs are immutable — a changed mind produces a new ADR that supersedes the old one. Format: Context → Decision → Alternatives considered → Consequences (including the bad ones).

| # | Decision | Status |
|---|---|---|
| [001](adr-001-monorepo.md) | Single repository for all services and frontend | Accepted |
| [002](adr-002-microservices.md) | Microservices over modular monolith | Accepted |
| [003](adr-003-database-per-service.md) | Database per service | Accepted |
| [004](adr-004-sync-vs-async.md) | Sync REST for queries, Kafka events for state propagation | Accepted |
| [005](adr-005-jwt-auth.md) | Stateless JWT, validated at the gateway | Accepted |
| [006](adr-006-spring-cloud-platform.md) | Eureka + Config Server + Spring Cloud Gateway | Accepted |
| [007](adr-007-mermaid-diagrams.md) | Mermaid-in-Markdown instead of draw.io files | Accepted |
| [008](adr-008-flyway-migrations.md) | Flyway for schema migrations | Accepted |
| [009](adr-009-transactional-outbox.md) | Transactional outbox for event publication | Accepted |
| [010](adr-010-remove-laboratory-service.md) | Remove laboratory-service and the unimplemented roles | Accepted |
| [011](adr-011-deployment-topology.md) | Deploy the full architecture to one free VM, not a reduced one to a PaaS | Accepted |
