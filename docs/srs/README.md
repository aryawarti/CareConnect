# CareConnect — Software Requirements Specification

Enterprise Hospital Management System. This SRS is the contract between the product
intent and the implementation: every service, screen and event in the codebase traces
back to a requirement ID here.

| # | Chapter | Document |
|---|---|---|
| 1–3 | Introduction · Business Requirements · Scope | [01-introduction.md](01-introduction.md) |
| 4 | Functional Requirements (21 modules) | [02-functional-requirements.md](02-functional-requirements.md) |
| 5 | Non-Functional Requirements | [03-non-functional-requirements.md](03-non-functional-requirements.md) |
| 6–7 | User Roles · Permission Matrix | [04-roles-and-permissions.md](04-roles-and-permissions.md) |
| 8 | Complete User Stories (per role) | [05-user-stories.md](05-user-stories.md) |
| 9–10 | Workflows · Business Rules | [06-workflows-and-rules.md](06-workflows-and-rules.md) |
| 11–14 | UI Wireframes · Dashboards · Navigation · Modules | [07-ui-and-navigation.md](07-ui-and-navigation.md) |
| 15–16 | Database Design · ERD | [08-data-model.md](08-data-model.md) |
| 17 | REST API Design | [09-api-design.md](09-api-design.md) |
| 18–20 | Microservices · Kafka Events · Notification Flow | [10-services-and-events.md](10-services-and-events.md) |
| 21–24 | Authentication · Authorization · Logging · Monitoring | [11-security-and-observability.md](11-security-and-observability.md) |
| 25–26 | Deployment · Testing Strategy | [12-deployment-and-testing.md](12-deployment-and-testing.md) |
| 27 | Future Enhancements · Delivery Roadmap | [13-roadmap.md](13-roadmap.md) |

## Requirement ID convention

`FR-<MODULE>-<n>` functional · `NFR-<n>` non-functional · `BR-<MODULE>-<n>` business rule
· `US-<ROLE>-<n>` user story. Code comments and commit messages cite these IDs, so any
line of the system can be traced to the reason it exists.

## Status legend used throughout

**Built** — implemented and running · **Partial** — core exists, module incomplete ·
**Planned** — specified here, not yet implemented. Honest status beats aspirational
documentation; a reader can trust what is marked Built.
