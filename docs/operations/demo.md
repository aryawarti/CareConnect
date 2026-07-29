# Demo Walkthrough

A 5-minute tour that exercises every architectural claim this project makes.

## 0. Start everything
```bash
cd backend && mvn -q package -DskipTests && cd ..
cd frontend && npm run build && cd ..
docker compose --profile platform up -d --build
docker compose ps          # wait for healthy (~2 min cold)
```
Set `SEED_ADMIN_EMAIL` / `SEED_ADMIN_PASSWORD` in `.env` before first start so an admin exists.

## 1. Seed a clinic (optional but fastest)
```powershell
.\scripts\seed-demo.ps1
```
Creates a doctor (account + profile + Mon–Fri availability), a patient with a completed profile, and one booked → confirmed → completed appointment. Everything goes through the public API, so the seeded state is state the system genuinely accepts.

## 2. The money shot: one event, three services
Keep this open in a second terminal:
```bash
docker logs -f careconnect-notification-service
```
In the UI, sign in as staff/admin → **Schedule** → mark a CONFIRMED appointment **Complete**. Within a second:

| Where to look | What appears | Which service |
|---|---|---|
| Doctor → **Charts** | a new encounter for that visit | medical-record-service |
| Patient → **Invoices** | an invoice for the snapshotted fee | billing-service |
| notification log | "thanks for visiting" + "invoice ready" | notification-service |

Nobody called those services. `AppointmentCompleted` did — three consumer groups, zero coupling.

## 3. Prove the guarantees

**No double-booking (database-enforced):** open the booking page in two browsers, select the same slot, submit both. One succeeds; the other gets a 409 "This slot has just been taken" — the Postgres exclusion constraint, not application luck.

**Idempotent payments:** on **My Invoices**, double-click *Pay now*. One payment, because the client-generated reference has a unique constraint.

**Clinical records are append-only:** as the doctor, write notes → **Sign encounter** → try to edit. Blocked with 409; a correction records an amendment preserving the previous text.

**Relationship-based access:** sign in as a second patient and request another patient's record ID directly — 403 despite a valid token and the right role.

**Failure isolation (NFR-2):**
```bash
docker compose stop notification-service billing-service
```
Book and complete an appointment — everything still works. Restart them:
```bash
docker compose start notification-service billing-service
```
The invoice and the emails appear within seconds: the events waited in Kafka. Now the other kind of dependency:
```bash
docker compose stop provider-service
```
Booking now fails fast with 503 "service temporarily unavailable" — we refuse to book what we cannot validate. That asymmetry is the design.

**No lost events (outbox, ADR-009):**
```bash
docker compose stop kafka
```
Complete an appointment — the UI succeeds. Check the backlog:
```bash
curl -s localhost:8084/actuator/metrics/careconnect.outbox.pending
```
Restart Kafka; the relay drains it and the downstream effects appear. The event survived a broker outage because it was committed with the business data.

## 4. Observability
```bash
curl -s localhost:8084/actuator/metrics/careconnect.appointments.booked
curl -s localhost:8086/actuator/prometheus | findstr careconnect
```
Every log line carries the correlation id assigned at the gateway; grep one id across services to replay a single user action end to end:
```bash
docker compose logs | findstr <correlation-id>
```

## 5. Platform internals worth showing
- Eureka dashboard: http://localhost:8761 — every service registered.
- Central config: `curl -s localhost:8888/appointment-service/default`
- Gateway edge auth: `curl -i localhost:8080/api/patients` → 401 problem+JSON before any service is touched.
