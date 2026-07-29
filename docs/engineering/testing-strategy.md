# Testing Strategy

Pyramid, per service — many fast unit tests, fewer slice tests, few full-context integration tests, plus a thin cross-service E2E smoke.

| Layer | Scope | Tools |
|---|---|---|
| Unit | domain logic (state machines, conflict rules, fee calc) — no Spring context | JUnit 5, AssertJ, Mockito |
| Web slice | controller + validation + error mapping | `@WebMvcTest`, MockMvc |
| Data slice | repositories, migrations against real Postgres | `@DataJpaTest` + Testcontainers |
| Integration | full context: REST in → DB + Kafka out | `@SpringBootTest` + Testcontainers (Postgres, Kafka) |
| Contract-ish | Feign client ↔ provider API compatibility | WireMock stubs generated from provider's own tests (Spring Cloud Contract deliberately skipped — weight vs. value at this scale; noted in interview notes) |
| E2E smoke | compose up → login → book → assert notification log & invoice | Script + REST assertions, Phase 9 |

Rules: Testcontainers over H2 (H2 lies about Postgres semantics — exclusion constraints, jsonb); every bugfix ships its regression test; coverage is a signal not a target, but domain packages should sit ≥80%.

What's deliberately not done: mutation testing, load testing beyond a demo script, full consumer-driven contracts — each noted with reasoning in interview notes.
