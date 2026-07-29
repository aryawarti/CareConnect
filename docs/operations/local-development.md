# Local Development

## Prerequisites

> **Docker Desktop needs 8 GB+ of memory** (Settings → Resources) to run the full
> stack comfortably: 10 JVMs, Postgres and Kafka. With less, services start but
> take minutes and healthchecks fail. Low on RAM? Start infra + platform only,
> and run business services on the host with `mvn spring-boot:run`.

Docker Desktop, JDK 21+ (project targets 21; builds verified on 25), Node 20+, Maven 3.9+.

## Infrastructure
```bash
cp .env.example .env          # then edit secrets
docker compose up -d          # PostgreSQL :5432, Kafka :9092
docker compose ps             # wait for healthy
```
Databases are auto-created on first start (`infra/postgres/init`). Reset everything: `docker compose down -v`.

## Services

Startup order: config-server → discovery-server → api-gateway → business services (any order).

**Option A — everything in Docker** (jars build on the host first — images are runtime-only):
```bash
cd backend && mvn -q package -DskipTests && cd ..
docker compose --profile platform up -d --build
```

**Option B — infra in Docker, services on the host (usual dev loop):**
```bash
docker compose up -d                                   # postgres + kafka only
cd backend
mvn -q -f config-server/pom.xml spring-boot:run        # terminal 1
mvn -q -f discovery-server/pom.xml spring-boot:run     # terminal 2
mvn -q -f api-gateway/pom.xml spring-boot:run          # terminal 3
```

**Phase 2 additions** — identity-service needs Postgres (`docker compose up -d`), then:
```bash
mvn -q -f identity-service/pom.xml spring-boot:run    # terminal 4 (or: in Docker via --profile platform)
cd frontend && npm install && npm start               # http://localhost:4200 (proxies /api to the gateway)
```
Register at http://localhost:4200/register, sign in, and the home page shows your roles — the full JWT round trip. Note: `mvn verify` now needs Docker running (Testcontainers integration test, by design).

**Phase 3** — patient-service joins the stack automatically (`--profile platform`). To use the staff UI, seed an admin first: set `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` in `.env`, restart identity-service, then sign in with it. Staff/admin see the Patients screen; patient accounts see My Profile.

**Verify the platform:**
```bash
mvn -q verify                                          # from backend/: builds + tests all modules
curl -i http://localhost:8080/api/_platform/config-health   # 200 via gateway → Eureka (lb://) → config-server
curl -s http://localhost:8888/api-gateway/default | head    # config served centrally
```
Every response from the gateway carries an `X-Correlation-Id` header — quote it when reading logs.

## Frontend

**Dev loop (recommended while coding):**
```bash
cd frontend && npm install && npm start   # http://localhost:4200, hot reload, proxies /api
```

**Containerized (production-style, part of the compose stack):**
```bash
cd frontend && npm run build && cd ..
docker compose --profile platform up -d --build frontend   # http://localhost:4300
```
nginx serves the built bundle and reverse-proxies `/api` to the gateway over the compose
network — same-origin, so the container setup needs no CORS at all.

## Useful
- Eureka dashboard: http://localhost:8761
- Kafka topics: `docker exec -it careconnect-kafka /opt/kafka/bin/kafka-topics.sh --bootstrap-server localhost:9092 --list`
- psql: `docker exec -it careconnect-postgres psql -U careconnect -d careconnect_patient`
