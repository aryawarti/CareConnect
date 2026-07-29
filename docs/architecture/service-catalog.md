# Service Catalog

Every service answers: *why does this exist as a separate deployable?* If a service couldn't justify itself, it was merged (see notes at bottom).

## Platform

### config-server (:8888)
Centralizes configuration in one versioned location; services fetch config at startup. Solves config drift across 10 modules. *Alternative:* per-service `application.yml` only — acceptable, but centralization demonstrates the pattern and enables profile-wide changes.

### discovery-server — Eureka (:8761)
Services register themselves; the gateway and Feign clients resolve instances by name instead of hard-coded URLs, enabling scaling and restarts without reconfiguration. *Alternative:* static URLs via config (fine in Compose, breaks when instances scale) or Kubernetes DNS (out of scope).

### api-gateway — Spring Cloud Gateway (:8080)
Single entry point: routing, JWT validation, CORS, rate limiting, correlation-ID injection. Keeps cross-cutting edge concerns out of every business service. *Alternative:* nginx (no Spring Security integration for JWT claims propagation), or no gateway (every service exposed + duplicated auth code).

## Business

### identity-service (:8081) — Identity & Access context
Owns users, credentials, roles, token issuance/refresh/revocation. Separate because auth has a distinct security posture, change cadence, and is consumed by everything. Merging it into any business service would tangle credentials with domain data.

### patient-service (:8082) — Patient context
Patient master data: demographics, contacts, activation state. Referenced (by ID) from appointments, records, billing. Small, stable, ideal first business service — it sets the structural template all others copy.

### provider-service (:8083) — Provider context
Doctor profiles, specialties, departments, availability schedules, consultation fees. Distinct from patients: different lifecycle, managed by staff/admin, and availability logic is its own subdomain. *Naming note:* "provider" (industry term) over "doctor" — leaves room for nurses/therapists without a rename.

### appointment-service (:8084) — Scheduling context, the core domain
Booking, conflict detection, lifecycle state machine, event publication. The system's collaboration hub: validates against patient/provider synchronously, then broadcasts facts asynchronously. Owning slots+bookings together keeps conflict checks in one ACID transaction.

### medical-record-service (:8085) — Clinical Records context
Encounters, diagnoses, prescriptions. Isolated because clinical data has the strictest access rules and an append-oriented model; a breach-boundary you want physically separated. Consumes appointment COMPLETED events to open encounters.

### billing-service (:8086) — Billing context
Invoices and (simulated) payments. Money has different auditors, rules, and failure tolerance than scheduling — classic separate context. Reacts to appointment events; never blocks clinical or scheduling flows.

### notification-service (:8087) — Notification context
Pure event consumer: templates + delivery (dev: logged). Separate because it's the natural place to demonstrate consumer-side concerns — idempotency, retries, dead-letter topics — and because notification outages must never affect business flows.

## Merge/split decisions
- **Slot management** stays inside appointment-service (transactional coupling with booking).
- **Departments** live in provider-service, not a separate "org service" — one table doesn't justify a deployable.
- **Pharmacy/Lab/Analytics**: deferred; would follow the same pattern without teaching new ones (ROADMAP, out-of-scope).
