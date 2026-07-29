# API Guidelines

Contracts before code: each service gets `docs/api/<service>.md` (plus springdoc-openapi at runtime) before its controllers are written.

## Conventions
- Base path via gateway: `/api/<resource>` (e.g., `/api/patients`, `/api/appointments`). Plural nouns, no verbs in paths; state changes that aren't CRUD use sub-resources (`POST /api/appointments/{id}/cancellation`).
- Versioning: none in v1 (single client we control). Strategy if needed later: URI prefix `/api/v2/...` — recorded here so the future decision has context.
- Pagination on every collection: `?page=0&size=20&sort=lastName,asc` → standard page envelope. Unpaged list endpoints are rejected in review.
- IDs are UUIDs; timestamps are ISO-8601 UTC.

## Response envelope
```json
{ "data": { }, "meta": { "page": 0, "size": 20, "totalElements": 137, "totalPages": 7 } }
```
`meta` present only for paged responses. No `success: true` booleans — HTTP status codes carry that.

## Errors — RFC 7807 Problem Details
```json
{
  "type": "https://careconnect.dev/errors/appointment-conflict",
  "title": "Appointment conflict",
  "status": 409,
  "detail": "Dr. Rao already has an appointment from 10:00 to 10:30.",
  "instance": "/api/appointments",
  "correlationId": "…",
  "errors": [ { "field": "startAt", "message": "overlaps existing appointment" } ]
}
```
Implemented once per service via `@RestControllerAdvice` + Spring's `ProblemDetail`. Validation failures → 400 with field errors; unknown routes → 404; auth → 401/403; conflicts → 409; unexpected → 500 with correlationId and **no stack traces or internals in the body**.

## Status code discipline
201 + `Location` header on creation · 204 for deletes · 409 for business-rule conflicts (not 400) · 422 avoided (400 covers it) — consistency beats pedantry.
