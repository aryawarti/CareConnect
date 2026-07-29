# Observability

Scope: the basics every production service needs, without a full LGTM stack.

## Logging
- Structured JSON via Logback encoder in all services; human-readable pattern in `local` profile.
- Every log line carries `service`, `correlationId` (from gateway / event envelope), `userId` when present.
- **Never logged:** passwords, tokens, medical data, full PII. Patient references are logged as IDs.

## Metrics & health
- Spring Boot Actuator on every service: `/actuator/health` (used by Compose healthchecks), `/actuator/metrics`, `/actuator/info` (git build info).
- Micrometer counters for domain events (appointments booked/cancelled, events published/consumed, DLT arrivals).
- Actuator endpoints are not exposed through the gateway.

## Tracing
Correlation-ID propagation (gateway → Feign → Kafka envelope) gives log-based tracing. Full distributed tracing (Micrometer Tracing + Zipkin) is a Phase 9 stretch — the propagation groundwork makes it a drop-in.

## Implemented in Phase 9
- **Correlation IDs end to end**: `platform-starter`'s auto-configured `CorrelationIdFilter` puts the gateway-issued id into every service's MDC; Feign forwards the header; Kafka events carry it in the envelope. One user action -> one id -> `docker compose logs | findstr <id>`.
- **Prometheus endpoint** on every service (`/actuator/prometheus`) plus domain metrics a clinic manager would actually ask for: `careconnect.appointments.booked`, `careconnect.appointments.conflicts`, `careconnect.invoices.issued`, `careconnect.invoices.paid`, and `careconnect.outbox.pending`/`published`/`failed` (the health signal for the event pipeline -- a rising pending gauge means the relay or broker is in trouble).

## Deliberately not done
A bundled Prometheus + Grafana stack. Metrics are exported and scrape-ready; standing up dashboards adds two containers and teaches nothing new at this scale. A decision, not an omission.
