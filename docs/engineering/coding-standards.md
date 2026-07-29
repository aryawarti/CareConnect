# Coding Standards

## Backend structure (identical in every service)
```
com.careconnect.<service>
├── api/            # controllers, request/response DTOs, mappers
├── application/    # services (use cases), transactions live here
├── domain/         # entities, enums, domain exceptions, repository interfaces
├── infrastructure/ # JPA repo impls/adapters, Feign clients, Kafka producers/consumers, config
```
Dependency rule: `api → application → domain`; `infrastructure` implements interfaces defined by `domain`/`application`. No controller touches a repository directly; no entity crosses the API boundary — DTOs and MapStruct mappers at the edge.

## Rules that get PRs rejected
- Entity returned from a controller · unpaged collection endpoint · field injection (`@Autowired` on fields — constructor injection only) · business logic in controllers · catch-and-ignore · `ddl-auto=update` · secrets in git · cross-service DB access.

## Naming
Services `<context>-service`; DTOs `CreatePatientRequest` / `PatientResponse`; events past-tense `AppointmentConfirmed`; Flyway `V<n>__<description>.sql`; branches `feature/<phase>-<topic>`; commits Conventional Commits (`feat(appointment): …`).

## Java
Java 21 features where they clarify (records for DTOs/events, pattern matching, sealed interfaces for event hierarchies). Lombok limited to `@Slf4j` — records and constructors cover the rest; less magic, better for a learning codebase.

## Angular
Standalone components; feature folders mirror backend contexts (`features/patients`, `features/appointments`); `core/` for interceptors/guards/api clients; `shared/` for reusable UI; RxJS: no nested subscribes — compose with operators; typed reactive forms only.

## Docs discipline
Code PRs that change behavior must touch: relevant doc page, CHANGELOG, and (if decision-bearing) an ADR. This is enforced by the PR template checklist.
