# Functional Requirements

Grouped by bounded context. Priorities: **M**ust, **S**hould, **C**ould (MoSCoW). IDs are stable and referenced from feature work.

## FR-A — Identity & Access
| ID | Requirement | Priority |
|---|---|---|
| FR-A1 | Users register with email + password; patients self-register, staff/doctor/admin accounts are created by admins | M |
| FR-A2 | Login issues a short-lived JWT access token and a refresh token | M |
| FR-A3 | Roles: ADMIN, DOCTOR, PATIENT, STAFF; every API enforces role-based access | M |
| FR-A4 | Token refresh and logout (refresh-token revocation) | M |
| FR-A5 | Password reset flow | S |

## FR-B — Patient Management
| ID | Requirement | Priority |
|---|---|---|
| FR-B1 | Create/read/update patient demographics, contacts, emergency contact | M |
| FR-B2 | Paginated patient search by name, phone, patient number | M |
| FR-B3 | Soft-deactivate patients (no hard deletes of medical-adjacent data) | M |
| FR-B4 | Patients see and edit only their own profile | M |

## FR-C — Provider Management
| ID | Requirement | Priority |
|---|---|---|
| FR-C1 | Manage doctors: profile, specialty, department, consultation fee | M |
| FR-C2 | Manage weekly availability schedules and exceptions (leave) | M |
| FR-C3 | Public directory: searchable list of doctors by specialty | M |

## FR-D — Appointment Scheduling
| ID | Requirement | Priority |
|---|---|---|
| FR-D1 | Patient books an available slot with a doctor; conflicts rejected | M |
| FR-D2 | Lifecycle: REQUESTED → CONFIRMED → COMPLETED / CANCELLED / NO_SHOW | M |
| FR-D3 | Cancellation rules (e.g., not within 2h of start) | S |
| FR-D4 | Doctor/staff daily schedule views | M |
| FR-D5 | Appointment state changes publish domain events | M |

## FR-E — Medical Records
| ID | Requirement | Priority |
|---|---|---|
| FR-E1 | Encounter opened from a completed appointment; doctor records notes, diagnoses, prescriptions | M |
| FR-E2 | Records are append-oriented: amendments, no destructive edits | M |
| FR-E3 | Access: treating doctors and the patient (read-only) | M |

## FR-F — Billing
| ID | Requirement | Priority |
|---|---|---|
| FR-F1 | Invoice auto-generated when an appointment completes (fee from provider data) | M |
| FR-F2 | Invoice lifecycle: ISSUED → PAID / VOID; simulated payment | M |
| FR-F3 | Patients view their invoices; staff manage all | M |

## FR-G — Notifications
| ID | Requirement | Priority |
|---|---|---|
| FR-G1 | Event-driven notifications: appointment confirmed/cancelled/reminder, invoice issued/paid | M |
| FR-G2 | Templated email content; delivery is simulated (logged) in dev | M |
| FR-G3 | In-app notification feed | C |
