# ADR-002: Microservices over modular monolith

**Status:** Accepted · 2026-07-18

## Context
For an outpatient clinic system with one developer and laptop-scale traffic, the *engineering-optimal* answer is a modular monolith: bounded contexts as modules, one deployable, no network partitions, refactorable boundaries. Any honest architect says so first.

## Decision
Build microservices anyway — because the primary requirement of this project is to *learn and demonstrate* distributed-systems engineering: service discovery, gateways, inter-service resilience, event-driven integration, eventual consistency. Those cannot be practiced in-process. The mitigation for the added complexity is strict scope: 7 business services, each mapping 1:1 to a bounded context, none smaller than a context ("no service per table").

## Alternatives
- **Modular monolith** — simpler ops, ACID everywhere, cheap refactoring; rejected only because it defeats the learning objective. This is the recommended production choice at this scale and is said so plainly in interview notes.
- **Monolith-first, extract later** — realistic industry path, but the extraction step is where the learning lives, and doing it twice doubles the work without doubling the lessons.

## Consequences
+ Every distributed-systems concept gets a concrete, runnable example.
− Higher operational overhead (10 processes), eventual consistency to manage, local dev needs Compose.
− Known risk: distributed monolith. Guard: services communicate only via documented APIs/events; chain depth 1; each service starts and runs alone.
