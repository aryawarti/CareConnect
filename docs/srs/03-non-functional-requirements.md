# 5 · Non-Functional Requirements

Every target below is measurable and, where marked Built, already verified in the codebase.

## 5.1 Performance
| ID | Requirement | Target | Status |
|---|---|---|---|
| NFR-1 | API read latency | p95 < 300 ms, p99 < 800 ms at 200 concurrent users | Partial |
| NFR-2 | API write latency | p95 < 500 ms | Partial |
| NFR-3 | Live queue update propagation | < 1 s from action to every subscribed screen | Built |
| NFR-4 | Patient history retrieval | Full longitudinal record < 3 s | Built |
| NFR-5 | Dashboard first paint | < 2 s on a 10 Mbps connection | Partial |
| NFR-6 | All list endpoints paginated | No unbounded result sets, ever | Built |
| NFR-7 | Read-heavy reference data cached | Formulary, tariffs, catalogues in Redis, TTL-bounded | Planned |

## 5.2 Scalability
| ID | Requirement | Target | Status |
|---|---|---|---|
| NFR-8 | Concurrent users | 1,000 per branch, 10,000 across branches | Planned |
| NFR-9 | Patient volume | 10 million records without redesign | Planned |
| NFR-10 | Services scale horizontally | Stateless; no sticky sessions | Built |
| NFR-11 | Event throughput | 5,000 events/minute sustained | Planned |
| NFR-12 | Database growth | Partitioning strategy for high-volume tables (audit, vitals, events) | Planned |

## 5.3 Availability & Resilience
| ID | Requirement | Target | Status |
|---|---|---|---|
| NFR-13 | Core clinical availability | 99.9% (≈ 43 min/month) | Planned |
| NFR-14 | Non-critical failure isolation | Billing/notification/analytics outages never block clinical care | Built |
| NFR-15 | Synchronous dependency failure | Fail fast with a clear message; never book what cannot be validated | Built |
| NFR-16 | No lost domain events | Transactional outbox; event shares the business transaction's fate | Built |
| NFR-17 | Duplicate delivery tolerance | All consumers idempotent via processed-event ledger | Built |
| NFR-18 | RPO / RTO | RPO ≤ 5 min, RTO ≤ 30 min | Planned |
| NFR-19 | Graceful degradation | Read-only clinical access if write path is impaired | Planned |

## 5.4 Security
| ID | Requirement | Status |
|---|---|---|
| NFR-20 | All traffic enters through the gateway; services are not directly reachable | Built |
| NFR-21 | JWT validated at the edge; identity forwarded as trusted headers that cannot be spoofed | Built |
| NFR-22 | Passwords hashed with BCrypt (cost 12); refresh tokens stored only as SHA-256 hashes | Built |
| NFR-23 | Refresh-token rotation with replay detection | Built |
| NFR-24 | Role-based access at method level, relationship-based at row level | Built |
| NFR-25 | Rate limiting on authentication and public endpoints | Planned |
| NFR-26 | TLS 1.2+ in transit; encryption at rest for database and object storage | Planned |
| NFR-27 | No PII, tokens or clinical data in logs | Built |
| NFR-28 | Secrets from environment/secret manager, never in source control | Built |
| NFR-29 | OWASP Top 10 addressed; dependency vulnerability scanning in CI | Planned |
| NFR-30 | Clinical record access logged (read access is auditable, not just writes) | Planned |

## 5.5 Data Integrity
| ID | Requirement | Status |
|---|---|---|
| NFR-31 | Double-booking impossible — database exclusion constraint, not application checking | Built |
| NFR-32 | Money in exact decimal types end to end; never floating point | Built |
| NFR-33 | Signed clinical records immutable; corrections are additive amendments | Built |
| NFR-34 | Every table carries created/updated actor and timestamp | Built |
| NFR-35 | Each service owns its schema; no cross-service database access | Built |
| NFR-36 | Schema evolution via versioned migrations with `ddl-auto=validate` | Built |
| NFR-37 | Cross-service consistency is eventual and explicitly documented per flow | Built |

## 5.6 Usability & Accessibility
| ID | Requirement | Status |
|---|---|---|
| NFR-38 | Role-specific navigation: a user sees only what their role acts on | Built |
| NFR-39 | Every empty state explains the next action | Built |
| NFR-40 | Responsive from 360 px (phone) to 2560 px (waiting-room display) | Partial |
| NFR-41 | WCAG 2.1 AA: contrast, keyboard navigation, focus order, ARIA labels | Planned |
| NFR-42 | Errors are actionable in plain language, never raw stack traces | Built |
| NFR-43 | Destructive actions require confirmation and are reversible where clinically valid | Partial |
| NFR-44 | Dark mode for night-shift ward and OT screens | Planned |

## 5.7 Maintainability
| ID | Requirement | Status |
|---|---|---|
| NFR-45 | Identical layered structure in every service (api → application → domain → infrastructure) | Built |
| NFR-46 | DTOs at every boundary; entities never serialised to clients | Built |
| NFR-47 | Uniform error contract (RFC 7807) across all services | Built |
| NFR-48 | Shared libraries limited to cross-cutting plumbing; never domain code | Built |
| NFR-49 | Architectural decisions recorded as ADRs with alternatives and costs | Built |
| NFR-50 | Requirement IDs referenced from code and commits | Partial |

## 5.8 Observability
| ID | Requirement | Status |
|---|---|---|
| NFR-51 | Correlation ID assigned at the edge, propagated through HTTP and events, in every log line | Built |
| NFR-52 | Structured JSON logs shipped to central storage (ELK) | Partial |
| NFR-53 | Prometheus metrics on every service, including domain metrics | Built |
| NFR-54 | Distributed tracing (OpenTelemetry) across services and Kafka | Planned |
| NFR-55 | Health probes distinguish liveness from readiness | Built |
| NFR-56 | Alerting on error rate, event backlog, consumer lag, queue wait times | Planned |

## 5.9 Compliance & Retention
| ID | Requirement | Status |
|---|---|---|
| NFR-57 | Clinical records retained per statute (7+ years; 21 for minors) | Planned |
| NFR-58 | Audit entries immutable and retained for the statutory period | Planned |
| NFR-59 | Patient data export on request (portability) | Planned |
| NFR-60 | Consent recorded for procedures and data sharing | Planned |
| NFR-61 | Data residency within the operating jurisdiction | Planned |
