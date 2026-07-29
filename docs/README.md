# CareConnect Documentation

Architecture-first: these documents are written and maintained *before and during* implementation, not after.

## Map

> **[Software Requirements Specification](srs/README.md)** — the full 27-chapter SRS:
> business requirements, all 21 modules, 10 roles with a permission matrix, complete user
> stories, workflows, data model, API contracts, event catalogue, security, deployment,
> testing and the delivery roadmap. Start there for *what* the system must do; the
> documents below cover *how* the built parts work.


| Section | Contents |
|---|---|
| [product/](product/) | [Vision](product/vision.md) · [Functional requirements](product/functional-requirements.md) · [Non-functional requirements](product/non-functional-requirements.md) |
| [architecture/](architecture/) | [High-Level Design](architecture/high-level-design.md) · [Service Catalog](architecture/service-catalog.md) · [Database Design](architecture/database-design.md) · [Communication & Events](architecture/communication.md) · [Security & Auth Flow](architecture/security.md) |
| [adr/](adr/) | [Architecture Decision Records](adr/README.md) — one file per decision, with alternatives and trade-offs |
| [api/](api/) | [API Guidelines](api/guidelines.md) · per-service API docs (added per feature) |
| [operations/](operations/) | [Local Development](operations/local-development.md) · [Observability](operations/observability.md) · [Troubleshooting](operations/troubleshooting.md) |
| [engineering/](engineering/) | [Coding Standards](engineering/coding-standards.md) · [Testing Strategy](engineering/testing-strategy.md) |
| [learning/](learning/) | [Interview Notes](learning/interview-notes.md) · [Learning Journal](learning/journal.md) |
| [ROADMAP.md](ROADMAP.md) | Phased delivery plan |

## Rules of the road

1. **A decision without an ADR didn't happen.** Anything with lasting consequences gets an ADR.
2. **Docs change in the same commit as the code they describe.**
3. **Diagrams are Mermaid** — text-diffable, reviewable in PRs (ADR-007).
4. Per-service API documentation is added under `api/` as each service is built; contracts before code.
