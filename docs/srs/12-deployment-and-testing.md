# 25 · Deployment

## 25.1 Environments
| Environment | Purpose | Topology |
|---|---|---|
| Local | Development | Docker Compose, one instance per service |
| CI | Automated verification | Ephemeral containers via Testcontainers |
| Staging | Pre-production validation | Kubernetes, production-like, anonymised data |
| Production | Live | Kubernetes, multi-replica, HA datastores |

## 25.2 Local (Built)
```bash
cp .env.example .env
cd backend  && mvn -q package -DskipTests && cd ..
cd frontend && npm install && npm run build && cd ..
docker compose --profile platform up -d --build
```
Runtime-only images (jars built on the host) keep image builds to seconds and avoid
dependency downloads inside Docker. JVMs run with C1-only JIT and capped heap so a laptop
can host the full stack. A seeder container populates a working hospital through the
public API, so the system is never empty on first run.

## 25.3 Kubernetes *(Planned)*
```
k8s/
  base/                    # Deployment, Service, ConfigMap, HPA, PDB per service
  overlays/{staging,prod}  # Kustomize: replicas, resources, secrets refs
  ingress.yaml             # TLS termination, path routing to the gateway
  postgres/ kafka/ redis/  # StatefulSets or managed-service bindings
  monitoring/              # Prometheus, Grafana, Alertmanager
```
Per service: liveness/readiness probes, resource requests and limits, `HorizontalPodAutoscaler`
(CPU 70%, min 2 / max 10), `PodDisruptionBudget` minAvailable 1, anti-affinity across
nodes. Secrets from the cluster secret store (or Vault), never in manifests.

## 25.4 Data stores
| Store | Local | Production |
|---|---|---|
| PostgreSQL | One container, database per service | Managed HA instance per service or logical DB, PITR backups, read replicas for analytics |
| Kafka | Single broker (KRaft) | 3+ brokers, replication factor 3, min.insync.replicas 2 |
| Redis *(Planned)* | Single container | Clustered, used for caching, rate limiting and SSE fan-out across replicas |
| MinIO / S3 *(Planned)* | MinIO container | S3 with versioning, SSE encryption, lifecycle rules |
| Elasticsearch *(Planned)* | Single node | 3-node cluster for logs and patient search |

## 25.5 CI/CD *(Planned)*
```
GitHub Actions
  on PR       : build, unit + slice tests, Testcontainers integration, SpotBugs,
                dependency-check, frontend lint + build
  on main     : build images, push to registry (tag = git sha), deploy to staging,
                run smoke suite
  on tag v*   : promote the same image to production, database migrations first,
                rolling deploy, automatic rollback on probe failure
```
Migrations run as a pre-deploy Job; every migration must be backward compatible with the
previous application version so a rollback never strands the schema.

## 25.6 Release safety
Rolling updates with surge 1 / unavailable 0. Feature flags for incomplete modules.
Blue/green for the gateway. Post-deploy verification: readiness probes, synthetic booking
transaction, error-rate watch for 15 minutes.

---

# 26 · Testing Strategy

## 26.1 Pyramid
| Layer | Scope | Tools | Status |
|---|---|---|---|
| Unit | Domain rules in isolation — state machines, money arithmetic, result flagging | JUnit 5, AssertJ, Mockito | Built |
| Slice | Controller contracts, validation, authorization (401/403 paths) | `@WebMvcTest`, MockMvc | Built |
| Data | Repositories and migrations against **real PostgreSQL** | `@DataJpaTest` + Testcontainers | **Not built** — schema and repositories are exercised only through the `@SpringBootTest` layer below |
| Integration | Full context: REST in → DB + events out | `@SpringBootTest` + Testcontainers (Postgres, Kafka) | Built (skips without Docker) |
| Contract | Feign client ↔ provider API compatibility | WireMock stubs | Built (appointment-service only) |
| End-to-end | Cross-service journeys through the gateway | Compose + REST assertions | Partial |
| UI | Component and journey tests | Jasmine/Karma; Playwright *(Planned)* | **Not built** — no `.spec.ts` exists |
| Performance | Load and soak | k6/Gatling *(Planned)* | Planned |
| Security | Dependency and OWASP scanning | OWASP Dependency-Check, ZAP *(Planned)* | Planned |

### Known gaps in coverage

Recorded rather than glossed over, because a status table that overstates itself
is worse than no table:

- **queue-service has no tests at all.** Token allocation under concurrency,
  priority ordering, wait estimation and the SSE broadcaster are all unverified.
  It is the newest service and the one with the most in-memory state, so this is
  the largest hole in the suite.
- **`mvn test` needs no Docker but skips 15 tests without it.** Every
  Testcontainers test is annotated `disabledWithoutDocker = true`, so a green
  run on a machine without Docker proves considerably less than it appears to.
  Check the `Skipped` count, not just `BUILD SUCCESS`.
- Coverage is not measured; the "≥ 80% on domain packages" target below is an
  intention, not an observation.

## 26.2 Rules that make the suite trustworthy
- **Testcontainers over H2.** H2 cannot express exclusion constraints, `jsonb`, or
  `SKIP LOCKED` — the very features the correctness of this system rests on.
- **Hermetic tests.** A test-classpath `application.yml` disables config-server and
  Eureka; a test must behave identically whether or not the local stack is running.
- **Every bug ships with its regression test.**
- **Coverage is a signal, not a target** — but domain packages should sit ≥ 80%.

## 26.3 What the critical tests actually prove
| Test | Guarantee |
|---|---|
| Concurrent booking of one slot | The database, not the application, prevents double-booking; the loser gets 409 |
| Cancel then rebook | The filtered exclusion constraint frees slots with zero cleanup code |
| Duplicate event delivery | One event, one notification/encounter/invoice — idempotency holds |
| Non-COMPLETED appointment events | Consumers ignore what isn't theirs; no phantom charts |
| Replayed refresh token | All sessions revoked — theft response works |
| Unrelated doctor reads one chart | 403 despite a valid DOCTOR role — relationship gating works |
| Unrelated doctor lists a patient's whole history | 403 — the *list* endpoint is gated on the treating relationship too, not only the single-encounter read |
| Doctor who treated patient A requests patient B | 403 — the check is per (patient, doctor), so treating anyone is not a key to everyone |
| Identity headers arriving without the gateway's shared secret | Stripped, request continues as anonymous → 401. A caller who reaches a service directly cannot claim to be an admin |
| Auth endpoint hammered from one address | 429 with `Retry-After` once the per-minute budget is spent, per client address so one abuser cannot lock out everyone |
| Service started with the repository's default JWT secret | Refuses to start unless local development opts in explicitly |
| Provider outage during booking | 503 fail-fast, no appointment created |
| Billing/notification outage | Consultation completes; effects appear on recovery |
| Kafka outage with outbox | Event survives, is delivered when the broker returns |
| Double-clicked payment | One payment; the second attempt is rejected by unique reference |

## 26.4 Test data
Deterministic seeds for reproducibility; anonymised production-shaped data in staging;
no real patient data outside production, ever.
