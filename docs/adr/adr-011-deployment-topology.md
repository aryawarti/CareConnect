# ADR-011: Deploy the full architecture to one VM, not a reduced one to a PaaS

**Status:** Accepted
**Date:** 2026-08-01
**Supersedes:** nothing. **Related:** [ADR-002](adr-002-microservices.md) (microservices),
[ADR-003](adr-003-database-per-service.md) (database per service),
[ADR-006](adr-006-spring-cloud-platform.md) (Eureka + Config Server)

## Context

CareConnect had to become reachable at a URL, at no cost, without ceasing to be the system
described in ADR-002. Those three constraints turn out to be in tension, and the tension is
the decision.

The obvious free option is a PaaS pair — a static host for the SPA and a container host for
the backend. Measured against what this system needs:

| Constraint | What the free PaaS tier gives |
|---|---|
| 11 long-running processes | ~750 instance-hours/month, shared. One process awake continuously costs 744 |
| 8 databases (ADR-003) | One, and on the most common provider it is deleted after 30 days |
| Kafka (ADR-009) | Nothing. There is no free managed Kafka worth deploying against |
| Fast first response | Free instances sleep after ~15 minutes; a cold Spring Boot chain takes minutes |

Each row independently forces the same concession: collapse the services into one process, the
databases into one schema, and the broker into in-process dispatch. Together they do not
describe a deployment of this architecture. They describe a different system that happens to
share a repository — and the parts that would be deleted are exactly the parts worth
discussing: the outbox, database ownership, the gateway trust boundary, service independence.

A free VM inverts every row. Oracle Cloud's Always Free Ampere A1 allocation is 4 OCPU and
24 GB of RAM, permanently, with block storage and a static public IP. The whole stack —
eleven JVMs, Postgres, Kafka, two proxies — budgets to roughly 8 GB.

## Decision

**Deploy the architecture unchanged to a single Oracle Cloud Always Free A1 VM, orchestrated by
Docker Compose, behind Caddy for TLS.**

Nothing is removed, merged or stubbed. Every service keeps its own container, its own database,
and its own Kafka topics. What changes is packaging and operations, not architecture.

Concretely:

1. **Multi-arch images.** A1 is ARM64. CI publishes `linux/amd64` *and* `linux/arm64` manifests,
   so the same tag runs on the VM, on a developer laptop, and on x86 orchestrator nodes later.
   This is close to free only because the Dockerfiles are runtime-only — nothing compiles under
   emulation.
2. **Secrets as mounted files, not environment variables.** Each secret is one file under
   `/run/secrets/`, read through Spring's `configtree` import. Rationale below.
3. **Compose split in two.** `docker-compose.prod.yml` is the service topology;
   `docker-compose.edge.yml` is TLS and the public hostname.
4. **Explicit resource and log ceilings.** Both are unbounded by default, and on a fixed VM both
   are how the machine dies.
5. **Caddy, not nginx+certbot,** as the TLS terminator, in front of the existing nginx.

## Why file-based secrets, specifically

This is the decision with the longest reach, so it deserves the argument rather than the
assertion.

Environment variables are the default way to configure a container and the wrong way to pass a
credential. They are visible in `docker inspect`, readable from `/proc` by anything sharing a
namespace, inherited by every child process, and printed by any library that logs its own
configuration on startup. Nothing about that is specific to this project; it is why Docker and
Kubernetes both grew a file-based mechanism.

The portability argument is what settles it. A Kubernetes Secret mounted as a volume produces
one file per key under one directory — byte-for-byte the layout Docker Compose produces. So:

| Concern | Compose (today) | Kubernetes | ECS |
|---|---|---|---|
| service | service | Deployment + Service | Task Definition + Service |
| `depends_on: service_healthy` | condition | readiness probe | container `dependsOn` |
| named volume | volume | PersistentVolumeClaim | EFS mount |
| **secret** | **file at `/run/secrets/X`** | **file at `/run/secrets/X`** | file or Secrets Manager |
| health check | `healthcheck` | liveness/readiness probe | health check |
| resource ceiling | `deploy.resources.limits` | `resources.limits` | task cpu/memory |
| TLS edge | Caddy (separate file) | Ingress + cert-manager | ALB + ACM |

Every row is a translation except the secret row, which is an identity. Secrets are normally
the row that forces application changes on migration — a different SDK, a different lookup, a
different startup path. Paying for `configtree` now means the future migration changes a mount
and nothing else.

The naming convention follows from the mechanism: files are `JWT_SECRET`, not
`careconnect.jwt.secret`, because `configtree` uses the filename verbatim as the property name
and the services already read `${JWT_SECRET}`. That behaviour is a framework guarantee we
depend on but do not own, so it is pinned by a test
(`platform-starter/.../FileSecretsConfigTreeTest.java`) rather than assumed.

## Alternatives considered

**Consolidate into a modular monolith for a free PaaS.** Genuinely viable and, for many
projects, correct — one JVM, one database with a schema per module, in-process events. Rejected
because it deletes the subject matter. The deployed artifact would no longer demonstrate
database-per-service, the transactional outbox, or the gateway trust boundary, and the
repository would then claim an architecture its live URL contradicts. Cost was also real
(entity-name collisions on `ProcessedEvent` and `OutboxEvent`, eight Flyway sets into one
datasource, and a WebFlux gateway that cannot share a JVM with servlet services), but cost was
not the deciding factor.

**Kubernetes (k3s) on the same VM.** Tempting, since the stated goal is eventual portability.
Rejected as premature: k3s on one node adds an API server, etcd and a CNI to a machine whose
scarce resource is RAM, in exchange for scheduling and rolling updates that a single node
cannot really provide. The portability this ADR buys comes from the *shape* of the
configuration, not from running an orchestrator early. Revisit when there is a second node.

**Fly.io / Railway / Koyeb.** More generous than the PaaS pair and would run several services,
but not eleven plus Kafka plus Postgres on a free plan, and each brings its own networking model
to migrate away from later.

**nginx + certbot instead of Caddy.** One fewer technology, and nginx is already in the stack.
Rejected because it moves certificate *renewal* into hand-written cron and a reload hook, and
renewal is the part that silently fails at the 90-day mark. Caddy's automatic issuance and
renewal is the entire reason to accept a second proxy.

**Cloudflare Tunnel instead of open ports.** Better security posture — no inbound ports at all
— and worth revisiting. Rejected for now because it replaces a standard reverse proxy with a
vendor-specific dial-out, which is further from an Ingress than the thing it replaces.

## Consequences

**Good.** The live system is the documented system; every guarantee in the README can be
demonstrated at the URL, including stopping Kafka and watching the outbox drain. Migration to a
real orchestrator is a translation exercise with no application changes. Backups, log rotation,
resource ceilings and health checks are configured rather than assumed.

**Bad, and accepted.**

- **Single point of failure.** One VM, one Postgres, one Kafka broker with
  `replication.factor=1`. No amount of Compose configuration changes that; it needs a second
  machine.
- **Deploys are a brief outage.** `compose up -d` recreates containers. Real zero-downtime needs
  an orchestrator that can run two versions at once. Documented in the README's honest
  limitations rather than papered over.
- **A1 capacity is not guaranteed.** Oracle frequently reports "out of host capacity" for the
  free ARM shape. This is the one step of the runbook with no engineering answer, only
  persistence.
- **Cold start is minutes.** Eleven JVMs chained through Eureka health gates. Fine for an
  always-on VM that starts once; it is why the PaaS sleep-on-idle model was disqualifying.
- **Eureka and Config Server earn little here.** On one host, Docker DNS and environment
  variables would do their jobs, and a Kubernetes migration would drop both. They are kept
  because ADR-002 and ADR-006 are the architecture under discussion, and because the gateway's
  `lb://` routes are written against them. Recorded so the question has an answer rather than a
  silence.
