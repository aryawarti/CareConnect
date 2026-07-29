# CareConnect Roadmap

Delivery is feature-by-feature: each phase produces something runnable and demonstrable, with docs and tests. Phases are ordered by dependency, not importance.

## Phase 0 — Foundation ✅
Repository structure, documentation tree, architecture design, ADRs, Docker Compose infrastructure (PostgreSQL, Kafka).

## Phase 1 — Platform services ✅
`config-server` → `discovery-server` (Eureka) → `api-gateway` (Spring Cloud Gateway).
*Outcome:* requests route through the gateway to a registered demo endpoint; centralized config proven.

## Phase 2 — Identity & the Angular shell ✅
`identity-service`: registration, login, JWT issue/refresh, roles (ADMIN, DOCTOR, PATIENT, STAFF). Gateway JWT validation filter.
Angular: app shell, Material layout, login/register, token interceptor, route guards.
*Outcome:* end-to-end authenticated request through the gateway.

## Phase 3 — Patient management ✅
`patient-service`: CRUD, search, pagination. Angular patient module.
*Outcome:* first full business feature; establishes the reference structure every later service copies.

## Phase 4 — Provider management ✅
`provider-service`: doctors, specialties, departments, availability schedules. Angular provider module.

## Phase 5 — Appointment scheduling (core domain) ✅
`appointment-service`: booking with conflict detection, lifecycle (REQUESTED → CONFIRMED → COMPLETED / CANCELLED / NO_SHOW). Sync validation of patient/provider via OpenFeign + Resilience4j (circuit breaker, timeouts). Angular booking flow.
*Outcome:* first inter-service communication, resilience patterns in practice.

## Phase 6 — Events & notifications ✅
Kafka producer in appointment-service (`appointment.events`), `notification-service` consumer with templated email (logged/SMTP-dev). Idempotent consumers, retry + DLT.
*Outcome:* event-driven architecture live.

## Phase 7 — Medical records ✅
`medical-record-service`: encounters, diagnoses, prescriptions; consumes `appointment.events` (COMPLETED) to open encounters. Role-restricted access. Angular records module.

## Phase 8 — Billing ✅
`billing-service`: invoice generated from COMPLETED appointment events, simulated payment, invoice lifecycle; publishes `billing.events` consumed by notifications.
*Outcome:* a realistic multi-service event chain (appointment → billing → notification).

## Phase 9 — Hardening & polish ✅
Delivered: `platform-starter` (auto-configured correlation-id filter — the one shared library ADR-001 permits), transactional outbox in all three event producers (ADR-009, closing the publish-after-commit gap), Prometheus endpoints + domain metrics, `scripts/seed-demo.ps1`, `scripts/redeploy.ps1`, and [docs/operations/demo.md](operations/demo.md) — a walkthrough that proves each architectural claim by breaking things on purpose.

## Deliberately out of scope (unless justified later)
Pharmacy/Laboratory/Analytics modules, Kubernetes, CQRS/event sourcing, microfrontends, multi-region concerns. Each would add surface area without teaching anything new at this project's scale — revisit only with a concrete reason (see ADR-002).
