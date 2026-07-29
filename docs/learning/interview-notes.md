# Interview Notes

Q&A distilled from this project's real decisions — grows every phase. The strongest answers name the trade-off and the road not taken.

### "Walk me through the architecture."
Outpatient clinic platform; 7 business services matching bounded contexts (identity, patient, provider, appointment, records, billing, notification) behind Spring Cloud Gateway with Eureka and central config. Sync Feign calls for validation reads only; every state change propagates as Kafka events. Database per service, UUID references, snapshots for facts that must not change retroactively (invoice amounts).

### "Why microservices for something this small?" (they *will* ask)
Honest answer: a modular monolith is the engineering-optimal choice at this scale — this was built as microservices deliberately, to practice distributed-systems patterns for real (ADR-002 says exactly that). Knowing when *not* to use microservices is the point of the answer.

### "How do you handle a service being down?"
Depends which one: sync dependencies (patient/provider checks during booking) → Resilience4j circuit breaker, fail fast with 503 — we don't book unverifiable appointments. Async consumers (billing, notification) → nothing fails; Kafka retains events, consumers catch up. That asymmetry is designed (NFR-2, ADR-004).

### "How do you avoid double-booking?"
Application-level conflict check plus a Postgres exclusion constraint on `(doctor_id, tstzrange)` — the DB is the last line of defense against races. Check-then-act alone is a race condition.

### "Exactly-once processing?"
Kafka gives at-least-once; exactly-once *processing* is achieved at the consumer: event ID recorded in `processed_events` in the same transaction as the side effect → duplicates become no-ops.

### "What's the weakest point of your design?"
Publish-after-commit: a crash between DB commit and Kafka publish loses an event. Fix is the transactional outbox (write event to an outbox table in the business transaction; a relay publishes). Scheduled for Phase 9 — known and documented beats accidental.

### "JWT revocation problem?"
Access tokens are irrevocable by design → keep them short (15 min); revocation lives on hashed, server-side refresh tokens. Instant-revocation requirements would push toward a token denylist or opaque tokens — different trade-off (per-request identity lookup).

*(extended each phase)*

### "How does Eureka actually work?"
Client-side discovery: services POST a registration, then renew a lease by heartbeat (30s default; eviction after 90s missed). Clients (gateway, Feign) fetch and cache the registry locally, so a registry outage degrades to stale-but-working routing — availability over consistency (AP). Self-preservation mode stops evictions when too many heartbeats fail at once (assumes network partition, not mass death); we disable it in dev for fast eviction, keep it in prod. Eureka is maintenance-mode software — the *pattern* is what transfers (Consul, K8s DNS do the same job).

### "Why is your gateway reactive when your services are MVC?"
The gateway is pure I/O fan-out — thousands of concurrent in-flight requests, almost no CPU per request: the event-loop model (Netty) shines and Spring Cloud Gateway only ships reactive. Business services are CRUD-over-JPA: blocking drivers, per-request work, simpler debugging — servlet MVC is the pragmatic fit. Mixed models are normal; choose per workload.

### "Config server precedence?"
For app `api-gateway`, profile `docker`: `application.yml` < `application-docker.yml` < `api-gateway.yml` < `api-gateway-docker.yml` — most specific wins. Native mode (classpath) keeps the monorepo self-contained; git-backed is the production pattern (audit trail, config changes as PRs) and is a drop-in swap.

### "Why disable the gateway's discovery locator?"
Auto-routing `/service-name/**` for anything that registers means deploying a service silently publishes it to the internet. Explicit routes make the edge a reviewed security surface. Convenience lost, attack surface controlled.

### "Why BCrypt for passwords but SHA-256 for refresh tokens?"
Passwords are low-entropy and guessable — they need a deliberately *slow*, salted hash (BCrypt cost 12) to make offline cracking expensive. Refresh tokens are 256-bit random strings — unguessable by construction, so slowness adds nothing; a fast one-way hash just prevents a DB leak from yielding usable tokens. Same-shaped problem, different threat model, different tool.

### "Walk me through a token refresh attack scenario."
Attacker steals a refresh token and uses it. Rotation means the legitimate user's next refresh presents a now-consumed token — that replay is detected, and we revoke every session for that user, forcing re-login. Detection-plus-blast-radius-limitation, since prevention alone is impossible once a token is exfiltrated.

### "Where is authorization enforced?"
Three layers: gateway does *authentication only* (valid JWT or 401 — no role logic at the edge); services do role checks via @PreAuthorize from trusted headers; data-level ownership checks live in application code (a patient sees only their own record). The gateway strips and *overwrites* inbound identity headers so they can't be forged from outside.

### "Why is the login error message identical for wrong password and unknown email?"
User enumeration: different messages (or different response *times*) let an attacker harvest valid emails. One generic message, and the register endpoint's 409 is the only unavoidable oracle (rate-limited at the gateway).

### "What's a real bug your tests caught?"
`@EnableJpaAuditing` on the Spring Boot application class broke every `@WebMvcTest`: the web slice excludes JPA, but the annotation forces creation of `jpaAuditingHandler`, which needs the JPA metamodel → context failure. The application class must stay annotation-minimal; cross-cutting config belongs in dedicated `@Configuration` classes that slices don't scan. Also a neat testing-pyramid argument: only a *slice* test could catch this — the full-context integration test passes because JPA is present there.

### "spring-boot-starter-parent vs BOM import?"
Starter-parent gives you plugin management AND property-based version overrides (`<byte-buddy.version>` just works). BOM import (needed when you have your own parent, like a multi-module monorepo) gives dependency versions only — property overrides are silently ignored because the BOM resolves its own properties. To override a BOM-managed version, declare the artifact explicitly in your own `dependencyManagement` (explicit entries beat imports). Found this when a version pin looked applied but the old jar was still on the classpath.

### "How do you prevent IDOR (accessing someone else's record)?"
Structurally, not defensively: patient-facing endpoints are `/api/patients/me`, and the service method takes the userId *from the validated token*, never from the URL. There is no code path where a PATIENT role supplies a patient ID — the vulnerable request shape doesn't exist. Role checks alone (`hasRole('PATIENT')`) would still allow `GET /api/patients/{someone-else}`; that's the junior mistake.

### "Why soft delete?"
Healthcare data has retention obligations and downstream references (appointments, records, invoices point at patient UUIDs). Hard deletes would orphan them. `status = INACTIVE` keeps referential integrity, history, and reversibility; queries filter by status where it matters.

### "How are MRNs generated safely under concurrent registration?"
Postgres sequence (`nextval`) — atomic, gap-tolerant, no table locks, no unique-retry loops. The human-readable formatting is application-side. A max(id)+1 approach would race; a UUID would be user-hostile at a front desk.

### "401 vs 403 — and who sends them?"
401 = "I don't know who you are" (missing/invalid credentials — from the AuthenticationEntryPoint); 403 = "I know who you are and the answer is no" (from the AccessDeniedHandler / method security). Spring's trap: with no entry point configured it emits 403 for anonymous users too, which misleads clients into not attempting login. We configure an explicit 401 entry point per service, and the gateway 401s invalid JWTs before services ever see them.

### "Why PUT-replace-all for the weekly schedule instead of CRUD per slot?"
Overlap validation needs the whole week in one place; per-slot edits validate against a moving target and can interleave badly under concurrency. Replace-all is idempotent (safe retries), transactional (rollback leaves the old schedule intact), and matches the UI (you edit the week, then save). Trade-off: larger payload, lost per-slot audit granularity — both acceptable here.

### "Where do authorization checks belong — annotations or code?"
Both, split by what they need: `@PreAuthorize` for pure role gates (no data required, declarative, visible in the controller). Service-layer code for ownership gates (requires loading the entity: is this doctor's user_id the caller's sub?). Putting ownership in SpEL annotations means hiding queries inside security expressions — hard to test, easy to get wrong.

### "How exactly is double-booking prevented under concurrency?"
Three layers: the UI only offers computed free slots; the service checks held appointments before insert; and the Postgres exclusion constraint (`btree_gist`, doctor_id equality + tstzrange overlap, filtered to slot-holding statuses) makes conflicting rows *unrepresentable*. Two concurrent bookings for the same slot → one commits, the other gets a constraint violation we translate to 409. The filtered constraint also means CANCELLED rows free their slot with zero code.

### "What happens when a service you depend on synchronously goes down?"
Feign calls run inside Resilience4j circuit breakers (10-call window, opens at 50% failures, 10s open). Failure or open circuit → DependencyUnavailableException → 503 with a clear problem type. We deliberately do NOT retry non-idempotent booking calls, and we do not book appointments we can't validate. Contrast with async consumers, which can't block booking at all — the failure-handling strategy tracks business criticality.

### "Why does appointment-service snapshot the fee instead of joining to provider data?"
An invoice must reflect the price at booking time; live joins would retroactively change history when fees change. Snapshot-what-must-not-change vs query-what-must-be-fresh (slot availability) — the same rule drives every cross-service data decision (database-design.md).

### "How do downstream services authorize service-to-service calls?"
The Feign interceptor forwards the original caller's X-User-Id/X-User-Roles (and correlation id). patient/provider expose minimal internal views (`/summary`, `/booking-info`) requiring authentication but no role, so a PATIENT booking for themselves resolves them. No shared service account — the deputy can't be confused about who it acts for.

### "Kafka is at-least-once — how do you get exactly-once behavior?"
You don't, at the delivery level — you get exactly-once *processing* at the consumer: record the eventId in a `processed_events` table in the same DB transaction as the side effect. Redelivered event → id exists → no-op. The atomicity of that transaction is the entire guarantee; an in-memory "seen" set dies with the pod, and a separate transaction reintroduces the race.

### "What happens to a message your consumer can't process?"
Retry 3× with backoff (transient failures), then the DeadLetterPublishingRecoverer moves it to `<topic>.DLT` with failure headers. The listener moves on — one poison message must not block the partition. DLT depth is a monitored metric; replaying from the DLT after a fix is a manual, deliberate act.

### "Why do producers define the topics?"
Broker auto-create is off (typo'd topic names fail loudly instead of silently creating garbage). The producer owns the contract — name, partitions — as code (`NewTopic` beans), versioned with the service that publishes. Consumers depend on the documented contract, never create it.

### "Where does the 'consumers never call back' rule come from?"
Self-contained event payloads (names, times, fee in the event). If notification-service had to call appointment-service to render an email, every consumer inherits the producer's availability — the coupling async was supposed to remove. Cost: payload duplication and eventual staleness; both acceptable for facts (the fee at booking time SHOULD be frozen).

### "What should a container healthcheck actually check?"
Readiness — "can this instance serve traffic" — not the full health aggregate. Spring's aggregate includes downstream indicators (Eureka, DB, broker); wiring that to a healthcheck means one flaky dependency marks healthy instances dead, orchestrators kill them, and the outage spreads. We hit exactly this: Eureka cache-refresh timeouts dragged the gateway's aggregate DOWN, so the frontend refused to start behind it. Fix: `/actuator/health/readiness` (readinessState group) for healthchecks, aggregate for humans/dashboards. Corollary lesson: `registry-fetch-interval-seconds` also serves as Eureka's task timeout, so "make dev snappier" (5s) quietly manufactured timeouts.

### "You proxy the API through nginx so it's same-origin — why did CORS still bite you?"
Because "same-origin" doesn't mean "no Origin header". Browsers send `Origin` on cross-*and*-same-origin POST/PUT/DELETE (not on simple GETs). Spring Cloud Gateway's CORS filter evaluates that header against the allowed list and returns a bodyless 403 before the route runs — so the symptom is "all my GETs work, all my POSTs 403", which looks like an auth bug and isn't. Diagnostic tell: 403 with `Content-Length: 0` and nothing in the downstream service log, meaning the request died at the edge.

### "Why can't a doctor just POST a new medical record?"
Because a chart's existence is a *consequence*, not an input: the encounter is created by the `AppointmentCompleted` event. That removes a whole class of data-integrity problems (charts for visits that never occurred, charts with mismatched patient/doctor) and makes the audit trail causal — every record traces back to a scheduled, completed appointment. The doctor enriches the chart; they don't conjure it.

### "How do you handle corrections to signed clinical notes?"
Never in place. Signing freezes the record; a correction creates an `Amendment` row holding the previous text plus a reason, updates the current note, and moves the status to AMENDED. Regulators (and lawyers) need to see what a record said at the time a decision was made — erasure destroys that. This is the same principle as append-only ledgers.

### "Role-based access isn't enough for medical data — what did you do?"
Relationship-based checks on top of role gates. `hasRole('DOCTOR')` only says the caller is *a* doctor; the service then asks whether they are *the treating* doctor for that specific encounter. Patients get the same treatment via ownership. Staff get read access for administrative work, and never write access to clinical content — the endpoints exist, the permission doesn't.

### "Walk me through what happens when a doctor marks a visit complete."
One local transaction in appointment-service (status → COMPLETED, history row), then one event on `appointment.events`. Three consumer groups pick it up independently: medical-record opens an encounter, billing issues an invoice using the fee snapshotted at booking, notification emails the patient. Billing then publishes `InvoicePaid`/`InvoiceIssued` on its own topic, which notification also consumes. No service knows who its consumers are; any of them can be down and catch up later without the clinic noticing.

### "How do you stop a double-clicked Pay button from charging twice?"
Client-generated idempotency key (`reference`) with a unique constraint on the payments table. The second submit violates the constraint and returns 409 rather than creating a second payment — and because it's enforced in the database, it holds across retries, concurrent tabs, and load-balanced instances. Application-level "have I seen this?" checks race; unique constraints don't.

### "Why not look up the current fee when billing?"
Because the patient agreed to a price at booking time. The fee travels in the event (snapshotted on the appointment row), so a doctor raising their rate tomorrow can't retroactively change today's invoice. Query-what-must-be-fresh, snapshot-what-must-not-change — the same rule that drives every cross-service data decision in this system.

### "Tell me about a bug where two correct components produced a wrong system."
Refresh-token rotation with replay detection (backend) + an interceptor that refreshes on 401 (frontend). Both are best practice. Together they log users out: when the access token expires, several in-flight requests 401 simultaneously, each triggers a refresh with the *same* stored token, the first rotates it, and the rest look like replay attacks — so the backend revokes every session, exactly as designed. The fix belongs on the client: refresh must be single-flight (one shared in-flight request that concurrent callers await). The general lesson is that security mechanisms with state transitions need a concurrency story on both sides of the wire.

### "Order of filters in an API gateway — why does it matter?"
Spring Cloud Gateway runs global filters and route filters in one ordered chain, and route filters run *after* highest-precedence global filters. We set trusted identity headers (`X-User-Id`, `X-User-Roles`) in a global JWT filter, and separately configured `RemoveRequestHeader` default-filters to strip spoofed inbound copies. The result: the strip ran last and deleted our own headers, so every authenticated request arrived at services anonymous and 401'd — while public routes worked perfectly, which made it look like a token bug. Lesson: a single component should own both the sanitize and the set, so the two can't be reordered relative to each other. Second lesson: a client that logs out on any persistent 401 will disguise server bugs as session expiry — only a failed *refresh* should end a session.

### "Explain the transactional outbox and why you needed it."
Publishing after commit can lose events (crash between the two); publishing before commit can invent them (transaction later rolls back). The outbox makes the event part of the business transaction: write to `outbox_events` in the same commit, then a relay publishes to Kafka and marks rows published. Ours polls every second, batches, and uses `SELECT ... FOR UPDATE SKIP LOCKED` so multiple instances can run without double-publishing, with a `pending` gauge as the alert signal. It's still at-least-once — the relay can crash after send, before commit — which is why consumers record `eventId` in `processed_events` inside their own side-effect transaction. Exactly-once across two systems would need XA or Kafka transactions; at-least-once plus idempotent consumers is the standard, simpler answer. The next step up is CDC (Debezium) reading the outbox table from the WAL — no polling at all.

### "When is a shared library the right call in microservices?"
When the code is genuinely cross-cutting, has no business meaning, and is duplicated identically everywhere — correlation-id propagation being the textbook case (ADR-001 said this in advance and we only created `platform-starter` in Phase 9 when seven copies existed). The danger is that shared libraries couple release cycles: a change to shared *business* code forces a coordinated deployment, which is exactly the distributed monolith microservices are supposed to avoid. Rule of thumb: share plumbing, never domain.
