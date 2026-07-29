# Communication & Event Design

## Choosing sync vs async

| Question being asked | Style | Mechanism |
|---|---|---|
| "Is this data valid *right now*?" (slot free? patient active?) | Synchronous request/response | REST via OpenFeign + Eureka, guarded by Resilience4j |
| "This fact happened." (appointment confirmed, invoice paid) | Asynchronous event | Kafka publish/subscribe |

Rules: a service may **query** another synchronously but never **command** it synchronously — state changes in other contexts happen only as reactions to events. This keeps write paths decoupled and makes NFR-2 (notification/billing outages don't break booking) structural rather than aspirational.

## Synchronous calls

Only two in v1, both from appointment-service:
- → patient-service: patient exists & active
- → provider-service: doctor availability & fee

Each Feign client gets: 2s connect / 3s read timeouts, circuit breaker (Resilience4j), and a documented fallback (booking fails fast with a clear 503 — we don't book unverifiable appointments). No retries on non-idempotent paths.

**Why OpenFeign:** declarative interface-based clients that integrate with Eureka and Resilience4j; alternative `RestClient` is fine but Feign reduces boilerplate across many clients. **Chain depth is capped at 1** — no service calls a service that calls a service; that's how latency and failure correlate.

## Kafka topics & contracts

| Topic | Producer | Events | Consumers |
|---|---|---|---|
| `appointment.events` | appointment-service | AppointmentRequested, AppointmentConfirmed, AppointmentCancelled, AppointmentCompleted, AppointmentNoShow | notification, medical-record, billing (each an independent consumer group) |
| `billing.events` | billing-service | InvoiceIssued, InvoicePaid, InvoiceVoided | notification |
| `patient.events` | patient-service | PatientRegistered (staff-created AND self-onboarded profiles) | notification |
| `queue.events` | queue-service | PatientCheckedIn, PatientCalled, ConsultationStarted, **ConsultationCompleted**, PatientSkipped, PatientLeft | appointment-service (completes the visit, which cascades to records/billing/notification) |
| `lab.events` | laboratory-service | LabRequested, SampleCollected, LabResultCritical, ReportUploaded | billing (raises the lab charge on LabRequested, idempotently), medical-record (links the released report to the encounter on ReportUploaded), notification (critical-value alert to the doctor; report-ready email to the patient) |

Partitioning key: aggregate ID (`appointmentId` / `invoiceId`) → per-aggregate ordering, which is the only ordering we need. 3 partitions per topic locally.

### Event envelope (JSON)
```json
{
  "eventId": "uuid",
  "eventType": "AppointmentConfirmed",
  "occurredAt": "2026-07-18T10:15:00Z",
  "aggregateId": "uuid",
  "version": 1,
  "correlationId": "uuid",
  "payload": { }
}
```
Events are **facts, past tense, self-contained** — payload carries what consumers need (patient contact snapshot, doctor name, time) so consumers avoid callbacks to producers. Schema changes are additive; breaking changes bump `version` and are documented here before code changes.

## Consumer reliability
- **Idempotency:** consumers record `eventId` in a `processed_events` table in the same DB transaction as their side effect; duplicates are skipped. Kafka is at-least-once — duplicates are a certainty, not an edge case.
- **Retries & DLT:** 3 retries with backoff, then dead-letter topic (`<topic>.DLT`). DLT monitoring is a troubleshooting-doc item.
- **Publishing:** events are written to an `outbox_events` table inside the business transaction and relayed to Kafka by a scheduled job (ADR-009). The event and the state change share a transaction's fate — no lost events on crash. The relay uses `FOR UPDATE SKIP LOCKED` (multi-instance safe) and exports a `careconnect.outbox.pending` gauge.

## Correlation
Gateway generates `X-Correlation-Id` per request; propagated via Feign interceptor and event envelope; logged by every service. One user action → one traceable thread through the whole system.
