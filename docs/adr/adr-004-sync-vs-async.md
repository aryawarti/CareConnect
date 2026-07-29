# ADR-004: Synchronous REST for queries, Kafka events for state propagation

**Status:** Accepted · 2026-07-18

## Context
Services need each other's data at two very different moments: validating an action *now* (is this slot free?) vs. reacting to something that *happened* (appointment completed → invoice).

## Decision
- **Queries**: REST via OpenFeign (Eureka-resolved), Resilience4j circuit breakers/timeouts, max chain depth 1.
- **Facts**: Kafka events with a standard envelope; consumers are idempotent; producers never know their consumers.
- A service never synchronously commands another service to change state.

## Alternatives
- **Everything sync** — simplest mental model, but availability multiplies: booking would fail when notification is down. Rejected via NFR-2.
- **Everything async (incl. queries)** — request/reply over Kafka adds latency and complexity for reads that need immediate answers; theoretical purity, practical pain.
- **RabbitMQ instead of Kafka** — excellent (simpler) choice for pure work queues; Kafka chosen for consumer groups with independent offsets (multiple contexts consume the same appointment events at their own pace), replayability, and its industry weight for event-driven systems. Trade-off: heavier ops, partition/ordering concepts to manage.
- **gRPC for internal calls** — real perf benefits at scale; unjustified extra toolchain here.

## Consequences
+ Failure isolation matches business criticality; new consumers subscribe without touching producers.
− Two integration styles to maintain; eventual consistency surfaces in UX (invoice appears "shortly after" completion) and must be handled in the UI.
− At-least-once delivery makes idempotency mandatory (enforced via `processed_events`).
