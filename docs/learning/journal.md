# Learning Journal

Chronological, per phase: what was built, what was learned, what surprised.

## Phase 0 — 2026-07-18 — Architecture & foundation
- Replaced the empty starter skeleton (15 blank docs, 5 empty .drawio files) with a structured doc tree; every foundational decision captured as an ADR with alternatives.
- Biggest design lesson: boundaries come from *language changes* in the domain, not from tables. "Patient" means three different things in three contexts — that's what justifies separate services, snapshot-vs-query decisions, and UUID-only references.
- Second lesson: the sync/async split isn't a technology preference, it's a question — "am I asking, or am I announcing?" Queries ask; events announce.

## Phase 1 — 2026-07-18 — Platform services
- Built config-server (native mode), discovery-server (Eureka), api-gateway (Spring Cloud Gateway) as Maven modules under a shared parent (BOM imports for Boot 3.4.x / Cloud 2024.0.x — no spring-boot-starter-parent, so one parent manages all modules).
- Config resolution order finally *clicked*: shared `application.yml` → `<service>.yml` → profile variants, later sources win. The config-repo is the single source of truth; per-service `application.yml` keeps only bootstrap concerns (name, config-server URL, port fallback).
- Gateway choice with teeth: discovery-locator **disabled** — auto-routing every registered service is convenient and quietly exposes anything that registers. Routes are declared explicitly; identity headers (`X-User-*`) are stripped at the edge before Phase 2 introduces them.
- Sandbox lesson: build environment had no route to Maven Central, so verification moved to a documented `mvn verify` + curl checklist instead of CI-in-chat. Constraint noted, not hidden.
- First real integration bug, found by running the system: the gateway's `lb://config-server` demo route 503'd because config-server never registered with Eureka — I'd given every *other* service the shared Eureka defaults through the config-repo, but config-server serves that file without consuming it. Fix: explicit Eureka client + config in config-server itself. Lesson: the config-server is always the special case in config-first setups; also, a 503 *with* your correlation header is a routing/registry problem, not a "gateway down" problem — the error's shape tells you which layer failed.

## Phase 2 — 2026-07-18 — Identity, JWT at the edge, Angular shell
- Refresh-token rotation was the deepest design point: each refresh consumes the token and issues a new one, and a *replayed* consumed token is treated as theft — the whole session family is revoked. Cheap to build, great interview material.
- Kept access tokens stateless/unstored and refresh tokens hashed server-side; the DB can leak without leaking usable credentials (BCrypt for passwords, SHA-256 for refresh tokens — different tools because one needs slowness, the other only one-wayness).
- The gateway JWT filter *overwrites* inbound X-User-* headers rather than merely stripping them — tested explicitly with a spoofed-header test.
- Sandbox reality: 45s process caps killed `ng new`/`npm install`, so the Angular workspace is hand-written (arguably better: no CLI boilerplate) and compile-verification happens on the dev machine.

## Phase 3 — 2026-07-19 — Patient management (the template service)
- patient-service is deliberately boring — that's the point. It encodes every convention (layering, envelope, 7807, hermetic tests, auditing config placement) so the next four services are mechanical copies with different domains.
- The `/me` endpoint pattern beats `if (isPatient) check ownership`: PATIENT-role queries are *scoped by the token's userId at the query level*, so addressing another patient's record isn't a forbidden request — it's an unrepresentable one. IDOR protection you can't forget to apply.
- `@CreatedBy`/`@LastModifiedBy` wired to the gateway's `X-User-Id` header via `AuditorAware` — every row knows who touched it, for free, forever.
- MRN from a Postgres sequence: unique under concurrency with zero locking code; formatting (`P-%06d`) stays in the app where it belongs.

## Phase 4 — 2026-07-19 — Provider management
- Replace-all availability (PUT the whole weekly schedule) over slot-by-slot CRUD: idempotent, validates overlaps against the complete picture, and a mid-update crash can't leave a half-written schedule — the transaction rolls back and the integration test proves the old schedule survives a rejected update.
- "Staff or owner" authorization needed *data* (does this doctor row belong to the caller?), so it lives in the service layer, not in @PreAuthorize — annotations gate roles; code gates ownership. Same lesson as patient /me, from the other direction.
- The public directory is the first gateway allowlist expansion — reviewed as a security-surface change, not a routing tweak.
- Closing the loop on FR-A1 (admin provisions accounts) was forced by this phase: a doctor without an identity account can't own a schedule. Features expose requirement gaps in dependency order.

## Phase 5 — 2026-07-19 — Appointment scheduling: the system becomes distributed
- The exclusion constraint is the phase's crown jewel: `EXCLUDE USING gist (doctor_id WITH =, tstzrange(start_at, end_at) WITH &&) WHERE (status IN ('REQUESTED','CONFIRMED'))`. The WHERE clause means cancelling *automatically* frees the slot — no cleanup code, the constraint simply stops caring about that row. Integration test books, conflicts, cancels, rebooks.
- Feign + Resilience4j asymmetry made concrete: sync validation dependencies fail fast (503, no booking), while future async consumers (Phase 6) will never block booking at all. NFR-2 is now enforced by architecture, not intention.
- Identity forwarding via a Feign interceptor was the subtle security decision: downstream services authorize the ORIGINAL caller. The alternative — a service account with god-rights — is how confused-deputy vulnerabilities are born.
- State machine in the aggregate (`transition()` private, named methods public) keeps "what's legal" in exactly one place; the service layer only orchestrates and records history.

## Phase 6 — 2026-07-19 — Events & notifications
- The UI exposed the requirement gap: a self-registered user had an account but no patient record, so /me endpoints 404'd and booking was impossible. Self-onboarding (POST /api/patients/me) closes the loop — and doubles as the first PatientRegistered event source. Lesson: walking the app as a user finds gaps that architecture diagrams hide.
- Idempotency is a transaction-shape problem, not a flag: the eventId lands in processed_events IN THE SAME transaction as the notification row. Crash after commit → redelivery is skipped; crash before → retry redoes everything. The embedded-Kafka test sends the same event twice and asserts one notification.
- Publish-after-commit's loss window is now real code, so it's documented at the publish site itself, with the outbox as the named fix (Phase 9). A gap you can point to beats a gap you forgot.
- Compose grew YAML anchors after I corrupted it with a careless scripted edit — the repair became the refactor.

## Phase 7 — 2026-07-21 — Medical records
- The most opinionated service so far, and the design writes itself once you take the domain seriously: no POST endpoint for encounters (a chart exists because a visit happened — the event is the only origin), no destructive edits after signing (amendments preserve prior text with a reason), and authorization by *relationship* rather than role (a DOCTOR token must not open every chart in the clinic).
- Double idempotency was a deliberate belt-and-braces call: `processed_events` stops the same event id being handled twice, while the unique `appointment_id` stops a *different* event id (producer replay, outbox migration in Phase 9) creating a second chart for one visit. Different failure modes, different guards.
- Consumers ignoring event types they don't care about is normal and healthy — medical-record-service subscribes to `appointment.events` and acts only on COMPLETED. A consumer subscribes to a topic, not to a curated feed.

## Phase 8 — 2026-07-21 — Billing, and the chain closes
- One event, three independent consumers: `AppointmentCompleted` now opens a medical encounter, issues an invoice, and sends an email — three services, three consumer groups, zero coupling between them. Adding the fourth consumer would require no change to the producer. That's the entire argument for event-driven architecture in one screenshot.
- Two idempotency keys with different jobs: `appointment_id` (unique) says "one visit, one invoice"; `payments.reference` (unique, client-generated) says "one submit, one charge". Conflating them would leave the double-click bug wide open.
- Money discipline is domain code, not documentation: BigDecimal + NUMERIC(10,2), no partial payments without a balance model, no voiding paid invoices (that's a refund — a different transaction with different accounting).

## Phase 9 — 2026-07-21 — Hardening
- The outbox finally closes the gap we documented in Phase 6 rather than hid: events now share a transaction with the state that caused them. Writing it made the trade-off vivid — the relay can still crash after `send()` and before commit, so the system is at-least-once *by design*, and that's precisely why every consumer was built idempotent three phases ago. The pieces were always meant to fit together.
- `platform-starter` is the first shared library, and ADR-001 predicted exactly when it would be justified: identical cross-cutting code in seven services (correlation IDs), zero business logic. Spring auto-configuration means services just add the dependency — no `@Import`, no cross-package component scanning.
- Metrics chosen by asking "what would a clinic manager ask?" — appointments booked, booking conflicts, invoices issued/paid — plus one operational gauge that actually pages someone: `outbox.pending`. Metrics nobody would act on are noise.
- The demo doc is deliberately built around *breaking* things (stop Kafka, stop provider-service, double-click Pay) because that's where the design decisions become visible. A demo that only shows the happy path proves nothing an interviewer cares about.

## Phase 10 — 2026-07-21 — The product pass (and an honest correction)
- Abhishek looked at the running app and said it made no sense: sign in, register, and empty screens. He was right, and the diagnosis was uncomfortable — I had built nine phases of architecture without ever making the system *visibly* alive. Every screen worked; every screen was blank, because seeding a clinic required a six-step admin workflow nobody would discover, and I only wrote the seed script in the final phase.
- Root cause, honestly: I optimized for what the brief emphasized (architecture, ADRs, trade-offs) and treated the frontend as proof the API worked. For a portfolio project that's backwards — a reviewer's first impression is the UI, and blank screens read as "nothing was built" no matter what the docs say.
- Fix in two parts. **Data**: a seeder that populates a realistic clinic *through the public API*, so events fire and encounters/invoices are produced by the system rather than inserted behind its back. **Design**: a real design system (tokens, cards, status pills, empty states that tell you what to do next), a landing page, and role-specific dashboards — because "home" means three different jobs to a patient, a doctor, and a receptionist.
- The lesson worth keeping: demo data is not a finishing touch, it's a Phase-1 concern. A system nobody can see working is indistinguishable from a system that doesn't work.

## Correction — 2026-07-21 — "This is just sign in and register"
- Abhishek sent screenshots of an empty app and asked whether the project had any point. The diagnosis was a one-line config default: `SEED_ADMIN_PASSWORD: ${SEED_ADMIN_PASSWORD:-}`. Empty by default meant the admin seeder skipped, so no administrator existed, so no doctor could ever be hired, so booking/records/billing had nothing to show. Eleven services, and the product was dead on arrival because of a blank string.
- Three lessons, all worth more than the feature work: (1) a system's default state IS a feature — "works after you read the docs and set an env var" is broken for anyone but the author; (2) the demo path deserves the same rigour as the happy path — I now seed a real clinic on startup and put one-click role logins on the sign-in page, because a reviewer must see all four roles inside a minute; (3) when someone says the project makes no sense, the useful reflex is to find what's structurally missing rather than explain harder — the architecture was fine, the on-ramp didn't exist.
