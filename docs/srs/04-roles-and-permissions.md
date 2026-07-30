# 6 · User Roles

Ten roles. Each exists because it has a distinct job and a distinct data boundary —
roles are not access tiers, they are working identities.

| Role | Who they are | Core responsibility | Data boundary |
|---|---|---|---|
> **Implemented today: ADMIN, DOCTOR, PATIENT, STAFF (receptionist) — and only those.**
> The rest of this table specifies roles for modules that are not built. They were
> previously seeded into the `roles` table, which made the system advertise authorities
> nothing enforced; [ADR-010](../adr/adr-010-remove-laboratory-service.md) removed them.
> Read the rows below as specification, not as capability.

| **Patient** | The person receiving care | Book, attend, read own records, pay | Only their own data |
| **Doctor** | Licensed clinician | Consult, diagnose, prescribe, order, admit | Own patients; own schedule; charts they treat |
| **Receptionist** | Front desk | Register, book, check in, collect payment | Operational data, no clinical content |
| **Nurse** | Ward clinical staff | Vitals, medication administration, ward care | Patients on their assigned ward/shift |
| **Lab Technician** | Laboratory | Sample handling, testing, result entry | Lab orders and results only |
| **Radiologist** | Imaging | Perform and report imaging studies | Radiology orders and reports |
| **Pharmacist** | Pharmacy | Dispense, manage stock, safety checks | Prescriptions and inventory |
| **Billing Executive** | Finance counter | Bills, payments, refunds, insurance claims | Financial data; patient identity only |
| **Administrator** | Branch management | Staff, departments, tariffs, verification, reports | Everything in their branch |
| **Super Admin** | Group/IT management | Branches, global config, cross-branch analytics, audit | All branches |

**Design principle:** clinical *content* is never visible to non-clinical roles.
Reception and billing see that a consultation happened and what it costs — never the
diagnosis. This is enforced at the API, not by hiding UI.

---

# 7 · Permission Matrix

Legend: **C** create · **R** read · **U** update · **D** deactivate (never hard delete)
· **—** no access · **own** limited to their own records · **rel** limited by clinical
relationship (treating doctor / assigned nurse).

## 7.1 Patient & clinical data
| Resource | Patient | Doctor | Reception | Nurse | Lab | Radiology | Pharmacy | Billing | Admin | Super Admin |
|---|---|---|---|---|---|---|---|---|---|---|
| Patient demographics | R/U own | R | C R U | R (ward) | R (order) | R (order) | R (rx) | R | C R U D | C R U D |
| Patient allergies | R own | C R U | R | C R U | R | R | R | — | R | R |
| Medical record (chart) | R own | C R U rel | — | R (ward) | — | — | — | — | R audit | R audit |
| Diagnosis | R own | C R U rel | — | R (ward) | — | — | — | — | R audit | R audit |
| Prescription | R own | C R U rel | — | R (ward) | — | — | R | — | R audit | R audit |
| Vitals | R own | R | — | C R U | — | — | — | — | R | R |
| Lab order | R own | C R rel | — | R (ward) | R U | — | — | R (billing) | R | R |
| Lab result | R own (released) | R rel | — | R (ward) | C R U | — | — | — | R | R |
| Radiology order/report | R own (released) | C R rel | — | R (ward) | — | C R U | — | R (billing) | R | R |

## 7.2 Operational
| Resource | Patient | Doctor | Reception | Nurse | Lab | Radiology | Pharmacy | Billing | Admin | Super Admin |
|---|---|---|---|---|---|---|---|---|---|---|
| Appointment | C R own, cancel | R own, accept/decline | C R U | R (ward) | — | — | — | R | R U | R |
| Doctor availability | R | C R U own | R | — | — | — | — | — | C R U | C R U |
| Queue token | R own | R U own queue | C R U | R | — | — | — | — | R | R |
| Admission / bed | R own | C R rel | C R | R U (ward) | — | — | — | R | C R U | R |
| Ward & bed master | — | R | R | R | — | — | — | — | C R U D | C R U D |
| Operation theatre | R own | C R rel | R | R | — | — | — | R | C R U | R |
| Emergency case | R own | C R U | C R U | C R U | — | — | — | R | R | R |

## 7.3 Pharmacy, inventory, finance
| Resource | Patient | Doctor | Reception | Nurse | Lab | Radiology | Pharmacy | Billing | Admin | Super Admin |
|---|---|---|---|---|---|---|---|---|---|---|
| Formulary | R | R | — | R | — | — | C R U | R | R | C R U |
| Dispensing | R own | R rel | — | R (ward) | — | — | C R U | R | R | R |
| Inventory stock | — | — | — | R (ward) | R | R | C R U | — | R | R |
| Purchase order | — | — | — | — | — | — | C R U | — | R U approve | R |
| Invoice | R own | R rel | R | — | — | — | R | C R U | R | R |
| Payment | C own | — | C R | — | — | — | C R | C R U | R | R |
| Refund | — | — | — | — | — | — | — | C (request) | U approve | U approve |
| Discount | — | — | — | — | — | — | — | C (limit) | U approve | U approve |
| Insurance claim | R own | — | R | — | — | — | — | C R U | R | R |
| Tariff master | R | R | R | — | R | R | R | R | C R U | C R U |

## 7.4 Platform
| Resource | Patient | Doctor | Reception | Nurse | Lab | Radiology | Pharmacy | Billing | Admin | Super Admin |
|---|---|---|---|---|---|---|---|---|---|---|
| Own profile | R U | R U | R U | R U | R U | R U | R U | R U | R U | R U |
| Staff accounts | — | — | — | — | — | — | — | — | C R U D | C R U D |
| Doctor verification | — | — | — | — | — | — | — | — | R U | R U |
| Roles & permissions | — | — | — | — | — | — | — | — | R | C R U |
| Branch master | — | — | — | — | — | — | — | — | R | C R U D |
| System configuration | — | — | — | — | — | — | — | — | R U (branch) | C R U (global) |
| Audit log | — | — | — | — | — | — | — | — | R (branch) | R (all) |
| Analytics | — | R own | R ops | R ward | R lab | R rad | R pharmacy | R finance | R branch | R all |

## 7.5 Enforcement rules
1. **Role gates the endpoint** (`@PreAuthorize`), **relationship gates the row** (service
   layer). A DOCTOR token is not a skeleton key to every chart.
2. **The gateway authenticates; services authorise.** Identity arrives as headers the
   gateway alone can produce.
3. **Branch scoping is a filter on every query**, not a UI concern.
4. **Deactivate, never delete.** Departed staff remain resolvable because their names
   appear on records they signed.
5. **Read access to clinical data is logged**, because in healthcare, looking is an act.
