# ADR-009: Transactional outbox for event publication

**Status:** Accepted · 2026-07-21 · *Supersedes the publish-after-commit approach introduced in Phase 6*

## Context
v1 published to Kafka after the database transaction committed:

```java
repository.save(appointment);   // committed
kafka.send(topic, event);       // crash here = event lost forever
```

The window is small but real, and the failure is silent: a completed appointment with no invoice and no medical record, discovered only when a patient complains. Documented as a known gap at the time (communication.md), scheduled for hardening.

## Decision
Write events to an `outbox_events` table **inside the business transaction**, and let a scheduled relay publish them to Kafka afterwards, marking rows published in its own transaction. Producers touched: patient-service, appointment-service, billing-service.

Relay details: polls every second, batches 100, uses `SELECT … FOR UPDATE SKIP LOCKED` so multiple instances never publish the same row, sends synchronously (only a real broker ack marks a row published), and stops the batch on failure so a broken broker doesn't burn attempts. `careconnect.outbox.pending` is exported as a gauge — a rising value is the alert that the relay or broker is unhealthy.

## Alternatives
- **Publish-after-commit** (what we had) — simplest, loses events on crash. Fine for notifications, not for invoices.
- **Publish-before-commit** — inverts the problem: invents events for transactions that later roll back. Strictly worse (phantom invoices beat missing ones only in the sense that both are wrong).
- **Kafka transactions + JDBC XA** — genuine exactly-once across two systems, at the cost of distributed transactions, coordinator ops, and reduced throughput. Rejected: the pattern this project teaches is at-least-once + idempotent consumers.
- **Debezium / CDC on the outbox table** — the production-grade version of this (no polling, no relay code, reads the WAL). Rejected for scope: it adds Kafka Connect to the stack. Noted as the natural next step, and the outbox table shape is already CDC-compatible.

## Consequences
+ No lost events: the event and the state change share a transaction's fate.
+ Retry, observability, and replay come free — pending rows are visible, countable, and re-publishable.
+ Multi-instance safe via `SKIP LOCKED`.
− Delivery latency rises by up to one poll interval (≈1s) — irrelevant for emails and invoices.
− Still at-least-once: the relay can crash after `send()` and before commit, re-sending on restart. Consumers were already idempotent (`processed_events`), which is what makes this acceptable.
− One more moving part per producing service (a scheduled job) and an outbox table to keep pruned (a cleanup job for published rows is a future task).
