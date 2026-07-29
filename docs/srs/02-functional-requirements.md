# 4 · Functional Requirements

Priority: **M** must-have · **S** should-have · **C** could-have.
Status: **Built** · **Partial** · **Planned**.

## 4.1 Identity & Access (IAM)
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-IAM-1 | Patients self-register with email and password | M | Built |
| FR-IAM-2 | Doctors self-register, then submit credentials for verification | M | Built |
| FR-IAM-3 | Administrators provision staff accounts (any role) with a temporary password | M | Built |
| FR-IAM-4 | Login issues a short-lived access token and a rotating refresh token | M | Built |
| FR-IAM-5 | Refresh-token replay is treated as theft: all sessions for that user are revoked | M | Built |
| FR-IAM-6 | Administrators revoke/restore access; accounts are never deleted | M | Built |
| FR-IAM-7 | Administrators reset a user's password; existing sessions end | M | Built |
| FR-IAM-8 | Password reset by email link (self-service) | S | Planned |
| FR-IAM-9 | Optional two-factor authentication for staff roles | S | Planned |
| FR-IAM-10 | Session list and remote sign-out for the account owner | C | Planned |
| FR-IAM-11 | Users are scoped to a branch; Super Admin operates across branches | M | Planned |

## 4.2 Patient Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-PAT-1 | Patient master record: demographics, contacts, emergency contact, address | M | Built |
| FR-PAT-2 | Permanent MRN/UHID generated at registration, gap-free and unique | M | Built |
| FR-PAT-3 | Patients complete their own profile after signup | M | Built |
| FR-PAT-4 | Reception registers walk-in patients who have no account | M | Built |
| FR-PAT-5 | Search by name, phone, MRN — paginated | M | Built |
| FR-PAT-6 | Patients edit contact details only; clinical identity (name, DOB) is staff-controlled | M | Built |
| FR-PAT-7 | Soft deactivation only; records are never hard-deleted | M | Built |
| FR-PAT-8 | Allergy and chronic-condition list maintained on the patient record | M | Planned |
| FR-PAT-9 | Patient merge/de-duplication with audit trail | S | Planned |
| FR-PAT-10 | Next-of-kin and consent capture for procedures | S | Planned |
| FR-PAT-11 | Insurance policy linkage (payer, policy number, validity, coverage) | M | Planned |

## 4.3 Doctor / Provider Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-DOC-1 | Doctor profile: name, specialty, department, qualification, registration number, experience, fee | M | Built |
| FR-DOC-2 | Credential verification workflow: PENDING → APPROVED/REJECTED by administration | M | Built |
| FR-DOC-3 | Only APPROVED and ACTIVE doctors appear in the patient directory | M | Built |
| FR-DOC-4 | Weekly availability windows with configurable slot length | M | Built |
| FR-DOC-5 | Schedule exceptions (leave, conference) block booking on those dates | M | Built |
| FR-DOC-6 | Doctors accept or decline appointment requests addressed to them | M | Built |
| FR-DOC-7 | Doctors manage their own availability without administrator help | M | Built |
| FR-DOC-8 | Doctor profile page visible to patients: bio, qualification, languages | S | Planned |
| FR-DOC-9 | Multi-branch practice: one doctor with schedules at several branches | S | Planned |

## 4.4 Appointment Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-APT-1 | Patients see only genuinely free slots (availability minus booked minus past) | M | Built |
| FR-APT-2 | Booking is impossible to double-book — enforced by a database exclusion constraint | M | Built |
| FR-APT-3 | Lifecycle: REQUESTED → CONFIRMED → COMPLETED / CANCELLED / NO_SHOW | M | Built |
| FR-APT-4 | Doctor accepts/declines; reception may confirm on their behalf for phone bookings | M | Built |
| FR-APT-5 | Patients cancel up to a configurable cutoff (default 2 hours) before start | M | Built |
| FR-APT-6 | Consultation fee is snapshotted at booking; later price changes never alter it | M | Built |
| FR-APT-7 | Every state change is recorded with actor and timestamp | M | Built |
| FR-APT-8 | Reschedule: move an appointment to another free slot, preserving history | M | Planned |
| FR-APT-9 | Follow-up appointments linked to the originating encounter | S | Planned |
| FR-APT-10 | Recurring appointments (dialysis, physiotherapy courses) | C | Planned |
| FR-APT-11 | Automated reminders 24 h and 2 h before the appointment | S | Planned |

## 4.5 Live Queue Module (OPD flow)
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-QUE-1 | Check-in from an appointment, or walk-in registration straight into a queue | M | Built |
| FR-QUE-2 | Sequential, gap-free token per doctor per day | M | Built |
| FR-QUE-3 | Triage priority (EMERGENCY/URGENT/NORMAL) reorders groups, preserving arrival order within a group | M | Built |
| FR-QUE-4 | Wait estimate from the median of the doctor's last 20 consultations | M | Built |
| FR-QUE-5 | Doctor console: call next, recall, start, complete, mark left | M | Built |
| FR-QUE-6 | Three unanswered calls auto-skip the token; the patient can be requeued | M | Built |
| FR-QUE-7 | Live updates over Server-Sent Events to every subscribed screen | M | Built |
| FR-QUE-8 | Public waiting-room display board, no login, no clinical data | M | Built |
| FR-QUE-9 | Patient sees own live position and estimate on their device | M | Built |
| FR-QUE-10 | Completing a consultation drives appointment completion and all downstream effects | M | Built |
| FR-QUE-11 | Queue analytics: average wait, walk-away rate, consultation duration per doctor | S | Partial |

## 4.6 EMR / Clinical Records Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-EMR-1 | Encounter created automatically from a completed consultation — never by manual POST | M | Built |
| FR-EMR-2 | Chief complaint and clinical notes | M | Built |
| FR-EMR-3 | Diagnoses with ICD-10 style codes | M | Built |
| FR-EMR-4 | Prescriptions: medication, dosage, frequency, duration, instructions | M | Built |
| FR-EMR-5 | Signing freezes the record; corrections are amendments preserving prior text | M | Built |
| FR-EMR-6 | Access by relationship: treating doctor writes, owning patient reads, staff read administratively | M | Built |
| FR-EMR-7 | Longitudinal patient history across all encounters, chronological | M | Built |
| FR-EMR-8 | Vitals recorded per encounter and per nursing round | M | Planned |
| FR-EMR-9 | Allergy and interaction warnings at the point of prescribing | M | Planned |
| FR-EMR-10 | Structured templates per specialty (SOAP notes) | S | Planned |
| FR-EMR-11 | Medical certificates and fitness letters generated as PDFs | S | Planned |
| FR-EMR-12 | Referral letters to other doctors or hospitals | C | Planned |

## 4.7 Laboratory Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-LAB-1 | Test catalogue: code, name, specimen type, department, price, normal ranges, TAT | M | Planned |
| FR-LAB-2 | Doctor orders tests from within an encounter; order is billable immediately | M | Planned |
| FR-LAB-3 | Order lifecycle: ORDERED → SAMPLE_COLLECTED → IN_PROCESS → REPORTED → VERIFIED | M | Planned |
| FR-LAB-4 | Barcode/accession number generated per sample; scanning drives every transition | M | Planned |
| FR-LAB-5 | Technician records results per analyte with units and reference ranges | M | Planned |
| FR-LAB-6 | Out-of-range values auto-flagged; critical values raise an immediate alert to the ordering doctor | M | Planned |
| FR-LAB-7 | Senior pathologist verification before a report is released to the patient | M | Planned |
| FR-LAB-8 | Report PDF stored in object storage; patient and doctor notified on release | M | Planned |
| FR-LAB-9 | Results attach to the originating encounter and appear in patient history | M | Planned |
| FR-LAB-10 | Sample rejection with reason (haemolysed, insufficient) and re-collection request | S | Planned |
| FR-LAB-11 | TAT monitoring per test and per technician | S | Planned |
| FR-LAB-12 | External/reference-lab outsourcing with tracking | C | Planned |

## 4.8 Radiology Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-RAD-1 | Modality catalogue (X-ray, USG, CT, MRI) with prep instructions and price | M | Planned |
| FR-RAD-2 | Order → scheduling against equipment/slot availability → performed → reported | M | Planned |
| FR-RAD-3 | Radiologist reporting with findings, impression and recommendation | M | Planned |
| FR-RAD-4 | Image file references stored in object storage (DICOM viewer out of scope) | M | Planned |
| FR-RAD-5 | Pregnancy/contrast-allergy safety checklist before ionising studies | M | Planned |
| FR-RAD-6 | Equipment utilisation and downtime tracking | S | Planned |

## 4.9 Pharmacy Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-PHR-1 | Formulary: generic and brand name, strength, form, schedule class, price | M | Planned |
| FR-PHR-2 | Prescription queue fed automatically from signed encounters | M | Planned |
| FR-PHR-3 | Dispense against a prescription with batch and expiry selection (FEFO) | M | Planned |
| FR-PHR-4 | Partial dispensing and substitution with pharmacist reason | M | Planned |
| FR-PHR-5 | Stock decrements on dispense; charge flows to the patient's bill | M | Planned |
| FR-PHR-6 | Over-the-counter sales without a prescription | M | Planned |
| FR-PHR-7 | Controlled-substance register with mandatory audit fields | M | Planned |
| FR-PHR-8 | Drug interaction and allergy check at dispensing | M | Planned |
| FR-PHR-9 | Returns and refunds of unused medication | S | Planned |
| FR-PHR-10 | Ward stock issue for inpatient administration | S | Planned |

## 4.10 Inventory Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-INV-1 | Item master: medicines, consumables, equipment; unit of measure, reorder level | M | Planned |
| FR-INV-2 | Batch-wise stock with manufacture and expiry dates per location | M | Planned |
| FR-INV-3 | Purchase orders to suppliers; goods receipt updates stock | M | Planned |
| FR-INV-4 | Expiry alerts at 90/60/30 days; expired stock quarantined automatically | M | Planned |
| FR-INV-5 | Reorder alerts when stock falls below level, factoring consumption rate | M | Planned |
| FR-INV-6 | Inter-location transfers (main store → ward → OT) | S | Planned |
| FR-INV-7 | Physical stock count with variance reconciliation | S | Planned |
| FR-INV-8 | Supplier master with lead times and rate contracts | S | Planned |

## 4.11 Admission (IPD) Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-IPD-1 | Ward and bed master: ward type, bed number, tariff, status | M | Planned |
| FR-IPD-2 | Admission from consultation or emergency; bed allocated and marked occupied | M | Planned |
| FR-IPD-3 | Live bed occupancy board by ward with cleaning/maintenance states | M | Planned |
| FR-IPD-4 | Bed transfer with reason and full movement history | M | Planned |
| FR-IPD-5 | Daily room charges accrue automatically until discharge | M | Planned |
| FR-IPD-6 | Attending doctor assignment and daily rounds notes | M | Planned |
| FR-IPD-7 | Discharge summary: diagnosis, course, procedures, medication, follow-up | M | Planned |
| FR-IPD-8 | Discharge blocked until the bill is settled or explicitly waived by authority | M | Planned |
| FR-IPD-9 | Estimated vs actual length of stay reporting | S | Planned |
| FR-IPD-10 | Death and DAMA (discharge against medical advice) recording | S | Planned |

## 4.12 Nursing Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-NUR-1 | Nurse sees only patients on their assigned ward and shift | M | Planned |
| FR-NUR-2 | Vitals capture: temperature, pulse, BP, respiration, SpO2, pain score | M | Planned |
| FR-NUR-3 | Vitals charted over time with abnormal-value highlighting | M | Planned |
| FR-NUR-4 | Medication administration record (MAR): due, given, missed, refused, with time and nurse | M | Planned |
| FR-NUR-5 | Nursing notes and shift handover summary | M | Planned |
| FR-NUR-6 | Alerts for overdue medication and critical vitals | M | Planned |
| FR-NUR-7 | Intake/output charting | S | Planned |
| FR-NUR-8 | Sample collection tasks assigned to nursing staff | S | Planned |

## 4.13 Operation Theatre Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-OT-1 | Theatre master with equipment and availability | M | Planned |
| FR-OT-2 | Surgery scheduling: surgeon, anaesthetist, team, theatre, duration | M | Planned |
| FR-OT-3 | Pre-operative checklist and consent verification | M | Planned |
| FR-OT-4 | Operative notes and implant/consumable usage recorded to billing | M | Planned |
| FR-OT-5 | Post-operative orders routed to nursing | M | Planned |
| FR-OT-6 | Theatre utilisation reporting | S | Planned |

## 4.14 Emergency Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-EMG-1 | Rapid registration: care starts before full demographics are captured | M | Planned |
| FR-EMG-2 | Triage levels (Immediate/Emergent/Urgent/Less urgent/Non-urgent) driving queue priority | M | Planned |
| FR-EMG-3 | Unknown-patient handling with temporary identity and later merge | M | Planned |
| FR-EMG-4 | Emergency-to-admission or emergency-to-discharge disposition | M | Planned |
| FR-EMG-5 | Ambulance arrival pre-notification | C | Planned |

## 4.15 Billing Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-BIL-1 | Invoice generated automatically from a completed consultation at the snapshotted fee | M | Built |
| FR-BIL-2 | Lifecycle ISSUED → PAID / VOID; paid invoices are never voided (refund instead) | M | Built |
| FR-BIL-3 | Payment with a client-supplied idempotency reference; duplicates rejected | M | Built |
| FR-BIL-4 | Patients view and pay their own invoices; staff collect at the counter | M | Built |
| FR-BIL-5 | Consolidated bill aggregating consultation, lab, radiology, pharmacy, room, procedures | M | Planned |
| FR-BIL-6 | Itemised charge lines with department, quantity, rate, tax | M | Planned |
| FR-BIL-7 | Discounts with reason and authorising user; percentage or absolute | M | Planned |
| FR-BIL-8 | Tax configuration per service category | M | Planned |
| FR-BIL-9 | Advance/deposit collection at admission, adjusted at final billing | M | Planned |
| FR-BIL-10 | Refunds with approval workflow | M | Planned |
| FR-BIL-11 | Printable/downloadable invoice and receipt PDFs | M | Planned |
| FR-BIL-12 | Daily collection reconciliation by counter and payment mode | S | Planned |

## 4.16 Insurance Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-INS-1 | Payer master: insurer, plan, coverage rules, exclusions | M | Planned |
| FR-INS-2 | Patient policy linkage with validity and sum insured | M | Planned |
| FR-INS-3 | Pre-authorisation request and approval tracking for planned procedures | M | Planned |
| FR-INS-4 | Bill split: insurer portion vs patient co-pay, computed from plan rules | M | Planned |
| FR-INS-5 | Claim submission packet with supporting documents | M | Planned |
| FR-INS-6 | Claim status tracking: submitted, queried, approved, partially approved, rejected, settled | M | Planned |
| FR-INS-7 | Ageing report of outstanding claims | S | Planned |

## 4.17 Notification Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-NOT-1 | Event-driven notifications; no business flow ever blocks on delivery | M | Built |
| FR-NOT-2 | Templated content per event type | M | Built |
| FR-NOT-3 | Idempotent consumption — one event, one notification | M | Built |
| FR-NOT-4 | Retry with backoff, then dead-letter topic | M | Built |
| FR-NOT-5 | Channels: email, SMS, in-app; per-user preferences | M | Partial |
| FR-NOT-6 | In-app notification centre with read/unread state | M | Planned |
| FR-NOT-7 | Delivery status tracking and failure visibility | S | Planned |

## 4.18 Analytics Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-ANA-1 | Role-specific dashboards with live operational figures | M | Partial |
| FR-ANA-2 | Revenue by department, doctor, service line and period | M | Planned |
| FR-ANA-3 | Patient flow: registrations, visits, admissions, discharges, occupancy | M | Planned |
| FR-ANA-4 | Clinical: top diagnoses, readmission rate, average LOS | S | Planned |
| FR-ANA-5 | Operational: average wait, TAT, no-show rate, theatre utilisation | S | Planned |
| FR-ANA-6 | Exportable reports (CSV/PDF) with date and branch filters | M | Planned |
| FR-ANA-7 | Cross-branch comparison for Super Admin | S | Planned |

## 4.19 Audit Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-AUD-1 | Append-only audit log of every create/update/delete on clinical and financial data | M | Planned |
| FR-AUD-2 | Records actor, role, action, entity, before/after, timestamp, IP, correlation id | M | Planned |
| FR-AUD-3 | Access log for record *reads* of clinical data (who viewed which chart) | M | Planned |
| FR-AUD-4 | Searchable and exportable for auditors; no user can edit or delete entries | M | Planned |
| FR-AUD-5 | Retention aligned to statutory minimums | M | Planned |

## 4.20 File Management Module
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-FIL-1 | Object storage (MinIO/S3) for reports, scans, consents, discharge summaries | M | Planned |
| FR-FIL-2 | Virus scan and MIME/type validation on upload | M | Planned |
| FR-FIL-3 | Time-limited pre-signed download URLs; no public buckets | M | Planned |
| FR-FIL-4 | Every access recorded in the audit log | M | Planned |
| FR-FIL-5 | Server-side encryption at rest | M | Planned |

## 4.21 Administration & Multi-branch
| ID | Requirement | Pri | Status |
|---|---|---|---|
| FR-ADM-1 | Staff onboarding: account, role, professional profile and schedule in one flow | M | Built |
| FR-ADM-2 | Department master | M | Built |
| FR-ADM-3 | Doctor credential verification queue | M | Built |
| FR-ADM-4 | Service tariff master per branch (consultation, lab, radiology, room, procedures) | M | Planned |
| FR-ADM-5 | Branch master; data scoped per branch; users assigned to branches | M | Planned |
| FR-ADM-6 | Role and permission management (custom roles beyond the built-in ten) | S | Planned |
| FR-ADM-7 | System configuration: cutoffs, slot defaults, tax rates, notification templates | M | Planned |
| FR-ADM-8 | Super Admin cross-branch console | S | Planned |
