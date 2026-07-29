# High-Level Design

## 1. From domain to boundaries

The domain splits into bounded contexts along lines where language and ownership change. "Patient" means demographics to the front desk, a schedule participant to scheduling, and a chart owner to a doctor — a classic sign these belong to different contexts:

| Bounded context | Core concept | Owns |
|---|---|---|
| Identity & Access | credentials, roles | users, tokens |
| Patient | demographics | patient master data |
| Provider | doctors & availability | doctor profiles, schedules, fees |
| Scheduling | the appointment lifecycle | appointments, slots |
| Clinical Records | the encounter | encounters, diagnoses, prescriptions |
| Billing | money | invoices, payments |
| Notification | messaging | templates, delivery log |

Each context becomes exactly one service. We did **not** split further (e.g., separate "slot service") — a boundary must earn its network hop.

## 2. System diagram

```mermaid
flowchart TB
    subgraph CL[Client]
        NG[Angular SPA]
    end
    subgraph PF[Platform]
        GW[API Gateway<br/>Spring Cloud Gateway :8080]
        EUR[Eureka :8761]
        CFG[Config Server :8888]
    end
    subgraph BIZ[Business Services]
        IDN[identity-service :8081]
        PAT[patient-service :8082]
        PRV[provider-service :8083]
        APT[appointment-service :8084]
        MED[medical-record-service :8085]
        BIL[billing-service :8086]
        NOT[notification-service :8087]
    end
    K[(Kafka)]
    DB[(PostgreSQL<br/>one DB per service)]

    NG -->|HTTPS + JWT| GW
    GW --> IDN & PAT & PRV & APT & MED & BIL
    APT -.->|OpenFeign| PAT
    APT -.->|OpenFeign| PRV
    APT ==>|appointment.events| K
    BIL ==>|billing.events| K
    K ==> NOT & BIL & MED
    BIZ --- DB
```

Solid = client traffic, dotted = sync service-to-service (validation reads), thick = Kafka events (state propagation). Notification-service is not routed through the gateway: it has no client-facing API in v1.

## 3. Key decisions (each has an ADR)

| Decision | Why (short) | ADR |
|---|---|---|
| Microservices, 7 business services | Learning goal is distributed systems; boundaries follow contexts | [ADR-002](../adr/adr-002-microservices.md) |
| Database per service | Enforces ownership; no cross-service joins by construction | [ADR-003](../adr/adr-003-database-per-service.md) |
| Sync REST for queries, Kafka for facts | Reads need answers now; state changes propagate as events | [ADR-004](../adr/adr-004-sync-vs-async.md) |
| Stateless JWT validated at gateway | Horizontal scale, no session store; edge does coarse auth, services do fine-grained | [ADR-005](../adr/adr-005-jwt-auth.md) |
| Eureka + Config Server + Gateway | Standard Spring Cloud triad; each solves a concrete problem at this scale | [ADR-006](../adr/adr-006-spring-cloud-platform.md) |

## 4. Request flow example — booking an appointment

```mermaid
sequenceDiagram
    participant P as Patient (Angular)
    participant G as Gateway
    participant A as appointment-service
    participant PR as provider-service
    participant PA as patient-service
    participant K as Kafka
    participant N as notification-service

    P->>G: POST /api/appointments (JWT)
    G->>G: validate JWT, forward claims as headers
    G->>A: route via Eureka lookup
    A->>PA: GET patient exists/active (Feign, circuit breaker)
    A->>PR: GET slot availability (Feign)
    A->>A: conflict check + persist (own DB, single ACID tx)
    A->>K: publish AppointmentConfirmed
    A-->>P: 201 Created
    K-->>N: consume → send confirmation email
```

The booking transaction is local to appointment-service — no distributed transaction. Downstream effects (notification, later billing) are eventually consistent via events. If notification-service is down, booking still succeeds; Kafka retains the event.

## 5. What we consciously rejected

- **Modular monolith** — the honest default for this team size, rejected because the express goal is practicing distributed systems (trade-off recorded in ADR-002).
- **Saga orchestration / distributed transactions** — no workflow here spans services with compensation needs in v1; booking is a local transaction plus events. Documented as a future topic in interview notes.
- **CQRS with separate read models** — no read/write asymmetry that justifies it.
- **Microfrontends** — one Angular app, feature-module per context.
