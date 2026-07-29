# ADR-008: Flyway migrations, `ddl-auto=validate`

**Status:** Accepted · 2026-07-18

## Context
Schema must evolve reproducibly across seven databases; `hibernate.ddl-auto=update` is nondeterministic and destructive-by-surprise — unacceptable outside toy projects.

## Decision
Every service manages its schema with versioned Flyway SQL migrations (`V1__init.sql`, …); Hibernate runs with `ddl-auto=validate` so the mapped model and real schema can never silently diverge.

## Alternatives
- **Liquibase** — equivalent capability, XML/YAML changelogs and rollbacks; Flyway's plain-SQL model is simpler and keeps SQL skills visible.
- **hibernate ddl-auto** — fine for prototypes; no history, no review, no prod story.

## Consequences
+ Reviewable schema history; identical DBs everywhere; migrations tested in Testcontainers.
− Writing SQL by hand (a feature, not a bug, for a learning project).
