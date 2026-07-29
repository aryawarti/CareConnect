# 27 · Delivery Roadmap & Future Enhancements

## 27.1 What exists today (verified running)
Eleven services and an Angular application implementing the outpatient spine:
identity with role-based access, patient master, doctor credentialing and availability,
appointment lifecycle with doctor acceptance, **live OPD queue with wait prediction and
public display boards**, clinical records with immutable signing, consultation-driven
billing, and event-driven notifications — over Kafka with a transactional outbox and
idempotent consumers.

## 27.2 Delivery plan
Each phase follows the clinical journey, not a technology layer, and ends with a working
demonstrable slice. Estimates assume one experienced full-stack engineer.

### Phase A — Diagnostics (Lab + Radiology) · ~2 weeks
`laboratory-service`, `radiology-service`, `file-service` (MinIO).
Doctor orders from the encounter → billable immediately → barcoded sample → technician
processing → result entry with reference ranges → critical-value alerting → senior
verification → PDF stored and released → doctor and patient notified → result attached to
the encounter.
**Demonstrates:** multi-step operational workflow, safety-critical alerting, object storage.

### Phase B — Pharmacy + Inventory · ~2 weeks
`pharmacy-service`, `inventory-service`.
Signed prescription → pharmacy queue → interaction/allergy checks → FEFO batch selection
→ dispensing → stock decrement → charge to bill; purchase orders, goods receipt, expiry
and reorder alerting; controlled-substance register.
**Demonstrates:** inventory ledger correctness, clinical safety checks, supply chain.

### Phase C — Admission, Wards, Nursing · ~3 weeks
`admission-service` (wards, beds, admissions, nursing, discharge).
Ward and bed master with a live occupancy board; admit from consultation or emergency;
daily room-charge accrual; nursing vitals and medication administration records; shift
handover; discharge summary gated on bill settlement.
**Demonstrates:** long-running stateful process, time-based accrual, shift-scoped access.

### Phase D — Consolidated Billing + Insurance · ~2 weeks
Extend `billing-service`; add `insurance-service`.
One episode bill aggregating consultation, lab, radiology, pharmacy, room and procedures;
itemised lines with tax; discounts with authority limits; advances and refunds; payer
plans, pre-authorisation, claim submission and settlement tracking.
**Demonstrates:** financial aggregation across services, approval workflows.

### Phase E — Emergency + Operation Theatre · ~2 weeks
Triage-first registration with temporary identities and later merge; theatre scheduling
with team and equipment; pre-op checklists; operative notes and consumable capture.

### Phase F — Analytics, Audit, Super Admin · ~2 weeks
`analytics-service` (revenue, flow, clinical and operational KPIs, exports),
`audit-service` (append-only writes and access logs, partitioned, searchable),
branch master and Super Admin console.

### Phase G — Platform hardening · ~2 weeks
Redis (caching, rate limiting, SSE fan-out across replicas), Elasticsearch (log
aggregation and patient search), Kubernetes manifests with autoscaling, Prometheus +
Grafana dashboards and alerts, OpenTelemetry tracing, GitHub Actions CI/CD, security
scanning, WCAG 2.1 AA pass, dark mode for night shifts.

**Total: roughly 15 weeks of focused work to complete the specification.**

## 27.3 Sequencing rationale
Diagnostics comes first because it is the most common clinical branch after consultation
and it exercises file storage and alerting — capabilities every later module reuses.
Pharmacy follows because prescriptions already exist and only need a consumer. Admission
is third because it is the largest new domain and depends on nothing but the ward master.
Billing consolidation waits until there are charges from several sources to consolidate —
building it earlier would mean building it twice.

## 27.4 Future enhancements beyond the specification
| Enhancement | Value | Complexity |
|---|---|---|
| Telemedicine consultations | Reach and continuity of care | Medium — video SDK plus a `visitType` already reserved |
| FHIR R4 API layer | Interoperability with national systems and devices | High — mapping layer over existing aggregates |
| Clinical decision support | Dose checking, protocol prompts, risk scores | High — rules engine plus curated knowledge base |
| ML no-show prediction | Smart overbooking recovers lost capacity | Medium — historical data already captured |
| Patient mobile app | Push notifications, offline records, queue on the go | Medium — APIs are already mobile-shaped |
| Voice-to-text clinical notes | Cuts documentation time dramatically | Medium — speech API integration |
| DICOM viewer integration | Radiologists read images in-platform | High — specialised viewer and storage |
| Bed-side tablets for nursing | Vitals captured at the point of care | Medium — responsive UI already exists |
| Queue-aware staffing suggestions | Uses live wait data to redeploy doctors | Medium — data already collected |
| Multi-language UI | Regional accessibility | Low–medium — i18n scaffolding |
| Blockchain-anchored audit hashes | Tamper evidence for regulators | Medium — periodic Merkle anchoring |
| Predictive bed management | Forecast discharges to plan admissions | High — needs LOS history |

## 27.5 Known technical debt
| Item | Impact | Plan |
|---|---|---|
| SSE fan-out is per-instance | A second queue-service replica would miss subscribers | Redis pub/sub relay in Phase G |
| Frontend orchestrates multi-service hiring | Front-office workflow spans two services | Acceptable; revisit if a BFF emerges |
| Fixed 20-sample wait model | Ignores time-of-day patterns | Time-bucketed model once volume justifies it |
| No branch scoping yet | Single-hospital assumption | Phase F introduces branch on every aggregate |
| Notification delivery is logged, not sent | Dev convenience | SMTP/SMS adapters are a configuration swap |
| Eureka is in maintenance mode | Long-term support risk | Pattern transfers to Consul or K8s DNS in Phase G |
