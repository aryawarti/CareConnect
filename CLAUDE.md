# CareConnect Claude Instructions

Act as a Staff Engineer and Tech Lead. Favor clean architecture, explain every decision, update docs continuously, and build feature-by-feature.

## Project Conventions

- **Docs live in `docs/`**; start at `docs/README.md`. Every feature/change updates the relevant doc and `CHANGELOG.md`. Decisions with lasting impact get an ADR in `docs/adr/` (one file per decision, never edit accepted ADRs — supersede them).
- **Diagrams are Mermaid in Markdown** (ADR-007). No binary/drawio diagram files.
- **Backend**: Java 21, Spring Boot 3, one Maven module per service under `backend/`. Package root `com.careconnect.<service>`. Layered: `api` (controllers, DTOs) → `application` (services) → `domain` (entities) → `infrastructure` (repos, clients, messaging). DTOs at boundaries, never expose entities. Flyway for schema. Standard API envelope + RFC 7807 errors (see `docs/api/guidelines.md`).
- **Frontend**: single Angular app under `frontend/`, feature modules mirroring backend services, standalone components, Material.
- **Events**: Kafka topic + payload contracts documented in `docs/architecture/communication.md` before implementation.
- **Workflow per feature**: requirement → design note/ADR → docs update → backend → frontend → tests → interview notes in `docs/learning/interview-notes.md`.
