# ADR-010: Remove laboratory-service and the unimplemented roles

**Status:** Accepted · 2026-07-30

## Context

`laboratory-service` was built as "the first hospital service beyond the clinic core": a
test catalogue with per-analyte reference ranges, a lab-order state machine, barcoded
accession numbers, result flagging, critical-value alerts and senior verification.
Roughly 2,000 lines of backend and 740 of frontend.

The problem is that the project's own scope statement excludes it. [The vision
doc](../product/vision.md) and the README both say plainly that this is **not** a hospital
system — "no beds, wards, surgery scheduling, pharmacy stock, **lab orders** or insurance
claims" — on the reasoning that *a clinic done properly beats a hospital done partially*.
Shipping a laboratory module directly contradicted the sentence above it.

The evidence supported the scope statement rather than the code:

- **Least tested service in the repository.** One test file (`ResultFlagTest`), covering
  result classification. No test for the order state machine, the accession numbering, or
  the verification gate — the parts that would actually matter.
- **Widest blast radius per unit of value.** It pulled in a ninth database, a fifth Kafka
  topic, a JVM, two frontend routes, an embedded panel in the encounter editor, and a role
  in the permission matrix.
- **It diluted the demo.** The thing worth showing is booking → queue → consultation →
  chart → invoice. A lab worklist is a second, unrelated story competing for attention in
  a walkthrough that is already long enough.

`identity-service` separately declared six roles — LAB_TECHNICIAN, RADIOLOGIST,
PHARMACIST, NURSE, BILLING, SUPER_ADMIN — of which four had **zero references anywhere in
the codebase**. A permission model that advertises authorities nothing enforces is worse
than a short one: it implies checks that do not exist.

## Decision

Remove `laboratory-service` entirely, along with its consumers
(`LabEventConsumer` in billing and medical-record), the `encounter_lab_reports` link
table, the `lab.events` topic subscription in notification-service, the frontend lab
module, and the seeder's lab pass.

Reduce the role set to the four the system actually enforces: ADMIN, DOCTOR, PATIENT,
STAFF.

`BillingService.issueForService` — the generic "bill for a non-appointment service" entry
point that existed solely for lab orders — goes with it. It had no other caller.

**Working code is not a reason to keep code.** The cost of a module is not what it took to
write; it is what it takes to run, test, document, explain and keep correct forever.

## Alternatives

- **Keep it and finish it properly** — write the missing tests, widen the scope statement
  to admit diagnostics. Rejected: it doubles the surface area to make the *scope statement*
  true, when changing one paragraph makes it true instead. The clinic core is not yet
  excellent; spending effort outside it is the wrong order.
- **Keep it, unfinished, and say so** — cheapest option, and what the repository was
  already doing implicitly. Rejected: the honest limitations list would grow to include
  "the lab module is barely tested", which is an admission that the module should not be
  there.
- **Extract to a separate repository** for later revival. Rejected as ceremony — git
  history is the archive, and this ADR is the pointer to it.

## Consequences

+ Eight services instead of nine; eight databases instead of nine. One less JVM, one less
  Kafka topic, ~2,800 fewer lines.
+ The scope statement in the README and vision doc is now true. That matters more than the
  feature did — a stated boundary the code ignores undermines every other claim.
+ The permission matrix lists only enforced roles.
+ The demo is a single coherent story.
− Diagnostics are gone as a demonstrated capability. The event-driven fan-out is still
  demonstrated by medical-record, billing and notification all reacting to
  `AppointmentCompleted`, so nothing architectural is lost with it.
− The `encounter_lab_reports` table is dropped by a **forward** migration
  (`V5__drop_encounter_lab_links.sql`) rather than by deleting `V2__encounter_lab_links.sql`.
  Flyway validates the checksum of every applied migration, so editing history would make
  the service refuse to start against any database that had already run it. Likewise
  `V3__remove_unimplemented_roles.sql` deletes role rows rather than rewriting V2 — and
  only where no user holds the role, so a cleanup migration can never silently strip an
  account's authority.

## Related

- Scope rationale: [vision](../product/vision.md)
- [ADR-002](adr-002-microservices.md) capped the architecture at "one service per bounded
  context, none smaller than a context". Laboratory *was* a bounded context, so it passed
  that test; what it failed was the product scope, which is a different question and the
  one this ADR answers.
