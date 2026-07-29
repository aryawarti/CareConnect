# 17 · REST API Design

## 17.1 Conventions
Base path `/api` through the gateway. Plural nouns; state changes that are not CRUD are
sub-resources (`POST /appointments/{id}/acceptance`), never verbs in paths. UUID ids,
ISO-8601 UTC timestamps. All collections paginated (`page`, `size`, `sort`).

**Success envelope**
```json
{ "data": {...}, "meta": { "page":0, "size":20, "totalElements":137, "totalPages":7 } }
```
**Error — RFC 7807 Problem Details**
```json
{ "type":"https://careconnect.dev/errors/appointment-conflict",
  "title":"Appointment conflict", "status":409,
  "detail":"Dr. Rao already has an appointment from 10:00 to 10:30.",
  "instance":"/api/appointments", "correlationId":"…",
  "errors":[{"field":"startAt","message":"overlaps existing appointment"}] }
```

**Status codes:** 200 read/update · 201 create with `Location` · 204 delete/no content ·
400 validation · 401 unauthenticated · 403 authorised-but-forbidden · 404 missing ·
409 business conflict (double-book, duplicate payment, illegal transition) ·
422 unused (400 covers it) · 429 rate limited · 503 dependency unavailable.

## 17.2 Identity — `/api/auth`, `/api/users`
| Method | Path | Auth | Body / Params | Response | Errors |
|---|---|---|---|---|---|
| POST | `/auth/register` | public | `{email, password, role: PATIENT\|DOCTOR}` | 201 tokens + user | 400, 409 email used |
| POST | `/auth/login` | public | `{email, password}` | 200 tokens | 401 (identical for wrong user/password) |
| POST | `/auth/refresh` | public | `{refreshToken}` | 200 new pair | 401 invalid/replayed → all sessions revoked |
| POST | `/auth/logout` | public | `{refreshToken}` | 204 | — |
| GET | `/users` | ADMIN, STAFF | page, size | 200 staff list | 403 |
| POST | `/users` | ADMIN | `{email, password, roles[]}` | 201 user | 400 unknown role, 409 |
| POST | `/users/{id}/deactivate` \| `/activate` | ADMIN | — | 200 user | 403, 404 |
| POST | `/users/{id}/password` | ADMIN | `{newPassword}` | 200 | 400, 403 |

## 17.3 Patients — `/api/patients`
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/patients?q=&page=&size=` | STAFF, ADMIN, DOCTOR | Search name/phone/MRN |
| POST | `/patients` | STAFF, ADMIN | Walk-in registration; MRN assigned |
| GET | `/patients/{id}` | STAFF, ADMIN, DOCTOR | — |
| PUT | `/patients/{id}` | STAFF, ADMIN | Full update |
| DELETE | `/patients/{id}` | STAFF, ADMIN | Soft deactivate → 204 |
| POST | `/patients/me` | PATIENT | Self-onboarding, once (409 after) |
| GET \| PUT | `/patients/me` | PATIENT | Own profile; contact fields only on PUT |
| GET | `/patients/{id}/summary` | authenticated | Minimal cross-service view |

## 17.4 Providers — `/api/providers`
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/providers/directory?q=` | **public** | APPROVED + ACTIVE only |
| GET | `/providers/departments` | public | — |
| POST | `/providers/apply` | DOCTOR | Self-application → PENDING |
| GET | `/providers/applications` | ADMIN, STAFF | Verification queue |
| POST | `/providers/doctors/{id}/approval` | ADMIN | → APPROVED, becomes bookable |
| POST | `/providers/doctors/{id}/rejection` | ADMIN | `{reason}` → REJECTED |
| POST | `/providers/doctors` | STAFF, ADMIN | Hire directly (APPROVED) |
| GET | `/providers/me` | DOCTOR | Own profile |
| GET \| PUT | `/providers/doctors/{id}/availability` | STAFF, ADMIN, owner | Replace-all weekly schedule |
| GET \| POST \| DELETE | `/providers/doctors/{id}/exceptions` | STAFF, ADMIN, owner | Leave days |
| GET | `/providers/doctors/{id}/booking-info?date=` | authenticated | One-call validation view |

## 17.5 Appointments — `/api/appointments`
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/appointments/available?doctorId=&date=` | authenticated | Computed free slots |
| POST | `/appointments` | PATIENT, STAFF, ADMIN | 409 on conflict, 503 if validation deps down |
| GET | `/appointments/me` | PATIENT | Own, newest first |
| GET | `/appointments/doctor/requests` | DOCTOR | Own inbox |
| POST | `/appointments/{id}/acceptance` \| `/decline` | DOCTOR (own) | Doctor's decision |
| POST | `/appointments/{id}/confirmation` | STAFF, ADMIN | On the doctor's behalf |
| POST | `/appointments/{id}/completion` \| `/no-show` | STAFF, ADMIN, DOCTOR | — |
| POST | `/appointments/{id}/cancellation` | owner PATIENT (≥2 h), STAFF, ADMIN | — |
| GET | `/appointments/day?date=` | STAFF, ADMIN | Clinic-wide |
| GET | `/appointments/doctor/{id}?date=` | STAFF, ADMIN, DOCTOR | Day schedule |

## 17.6 Queue — `/api/queue`
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/queue/stream/{doctorId}` | **public** | Server-Sent Events, snapshot per change |
| GET | `/queue/board/{doctorId}` | **public** | Snapshot (kiosk boot / fallback) |
| POST | `/queue/check-in` | STAFF, ADMIN, PATIENT | From appointment; idempotent |
| POST | `/queue/walk-in` | STAFF, ADMIN | No appointment |
| POST | `/queue/doctor/{id}/call-next` | DOCTOR, STAFF, ADMIN | Fairness order |
| POST | `/queue/{id}/recall` \| `/start` \| `/complete` \| `/left` \| `/requeue` | DOCTOR, STAFF, ADMIN | Console actions |
| GET | `/queue/me` | PATIENT | Live position, ETA, message |
| GET | `/queue/live` | STAFF, ADMIN | Clinic-wide |

## 17.7 Medical records — `/api/records`
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/records/me` | PATIENT | Own history |
| GET | `/records/doctor/me` | DOCTOR | Own encounters |
| GET | `/records/patient/{id}` | DOCTOR, STAFF, ADMIN | Patient history |
| GET | `/records/{id}` | relationship-checked | 403 if unrelated, even with a valid role |
| PUT | `/records/{id}` | treating DOCTOR | Only while OPEN |
| POST | `/records/{id}/diagnoses` \| `/prescriptions` | treating DOCTOR | Only while OPEN |
| POST | `/records/{id}/signature` | treating DOCTOR | Requires notes; freezes record |
| POST | `/records/{id}/amendments` | treating DOCTOR | `{notes, reason}`; preserves prior |

## 17.8 Laboratory — `/api/lab` *(Planned)*
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/lab/catalogue?q=` | clinical roles | Tests, prices, TAT |
| POST | `/lab/orders` | DOCTOR | `{encounterId, items[], priority, indication}` → charge raised |
| GET | `/lab/orders?status=&priority=` | LAB | Worklist, STAT first |
| POST | `/lab/orders/{id}/items/{itemId}/collection` | LAB | Barcode bound → SAMPLE_COLLECTED |
| POST | `/lab/samples/{accession}/processing` | LAB | → IN_PROCESS |
| POST | `/lab/samples/{accession}/rejection` | LAB | `{reason}` → re-collection requested |
| PUT | `/lab/orders/{id}/items/{itemId}/results` | LAB | Values per analyte; auto-flagging |
| POST | `/lab/orders/{id}/verification` | SENIOR LAB | Releases report, notifies |
| GET | `/lab/orders/{id}/report` | doctor, owner patient | Pre-signed PDF URL |

## 17.9 Pharmacy & inventory — `/api/pharmacy`, `/api/inventory` *(Planned)*
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/pharmacy/queue` | PHARMACIST | Prescriptions awaiting dispensing |
| GET | `/pharmacy/formulary?q=` | clinical roles | Search medicines |
| POST | `/pharmacy/dispense` | PHARMACIST | Lines with batch; FEFO validated; stock decrements |
| POST | `/pharmacy/counter-sales` | PHARMACIST | OTC sale |
| GET | `/inventory/stock?itemId=&location=` | PHARMACIST, ADMIN | Batch-wise |
| GET | `/inventory/alerts` | PHARMACIST, ADMIN | Expiry + reorder |
| POST | `/inventory/purchase-orders` | PHARMACIST | → ADMIN approval |
| POST | `/inventory/goods-receipts` | PHARMACIST | Against a PO; stock increments |

## 17.10 Admission & nursing — `/api/admissions`, `/api/nursing` *(Planned)*
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/admissions/beds?ward=` | STAFF, ADMIN, NURSE | Live occupancy board |
| POST | `/admissions` | STAFF, ADMIN | Allocate bed → `PatientAdmitted` |
| POST | `/admissions/{id}/transfer` | STAFF, ADMIN, NURSE | `{toBedId, reason}` |
| POST | `/admissions/{id}/discharge-summary` | attending DOCTOR | Clinical summary |
| POST | `/admissions/{id}/discharge` | STAFF, ADMIN | Blocked while unsettled |
| GET | `/nursing/my-ward` | NURSE | Assigned patients, tasks |
| POST | `/nursing/admissions/{id}/vitals` | NURSE | Abnormal → doctor alert |
| GET | `/nursing/admissions/{id}/medication-due` | NURSE | Schedule |
| POST | `/nursing/medication/{id}/administration` | NURSE | given/missed/refused + reason |

## 17.11 Billing & insurance — `/api/invoices`, `/api/claims`
| Method | Path | Auth | Notes |
|---|---|---|---|
| GET | `/invoices/me` | PATIENT | Own |
| GET | `/invoices?status=` | BILLING, ADMIN | Work queue |
| GET | `/invoices/{id}` | owner, BILLING, ADMIN | Itemised |
| POST | `/invoices/{id}/payments` | owner PATIENT, BILLING | `{amount, method, reference}`; duplicate → 409 |
| POST | `/invoices/{id}/discount` | BILLING (limit), ADMIN | `{amount, reason}` *(Planned)* |
| POST | `/invoices/{id}/void` | BILLING, ADMIN | Never on PAID |
| POST | `/invoices/{id}/refunds` | BILLING → ADMIN approval | *(Planned)* |
| POST | `/claims` | BILLING | Submit packet *(Planned)* |
| GET | `/claims?status=` | BILLING, ADMIN | Ageing *(Planned)* |

## 17.12 Platform — files, analytics, audit *(Planned)*
| Method | Path | Auth | Notes |
|---|---|---|---|
| POST | `/files` | clinical roles | Multipart; virus + MIME check; returns key |
| GET | `/files/{key}/url` | permission-checked | Time-limited pre-signed URL; access logged |
| GET | `/analytics/revenue?from=&to=&groupBy=` | ADMIN | Department/doctor/service |
| GET | `/analytics/operations` | ADMIN | Waits, TAT, occupancy, no-shows |
| GET | `/audit?actor=&entity=&from=&to=` | ADMIN, SUPER_ADMIN | Immutable, exportable |

## 17.13 Validation and idempotency
Every request DTO is bean-validated; violations return 400 with per-field messages.
Idempotency keys are required where money or clinical acts are involved: payment
`reference`, queue check-in (per appointment), event consumption (`eventId`). Booking
relies on the database exclusion constraint rather than optimistic checks.
