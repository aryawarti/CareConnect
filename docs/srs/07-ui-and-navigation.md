# 11 · UI Wireframe Description

## 11.1 Design language
Clinical software is used for eight-hour shifts under pressure. The interface is
therefore quiet: one accent colour, generous whitespace, high-contrast text, and colour
reserved for meaning (status, urgency) rather than decoration.

| Token | Value | Used for |
|---|---|---|
| Primary | `#0f766e` deep teal | Actions, active navigation, brand |
| Accent | `#b45309` amber | Money, attention-needed states |
| Success | `#15803d` | Paid, confirmed, verified, signed |
| Warning | `#b45309` | Pending, awaiting action |
| Danger | `#b91c1c` | Cancelled, critical values, void |
| Ink / soft / faint | `#0f172a` / `#475569` / `#94a3b8` | Text hierarchy |
| Canvas / surface | `#f6f8fa` / `#ffffff` | Page and card backgrounds |
| Radius | 14 px cards, 10 px controls | — |

Typography: Roboto; 30/22/17/14 px scale; tabular numerals for all money and vitals so
columns align. Icons: Material Symbols, outlined, 20 px inline.

## 11.2 Standard page anatomy
```
┌──────────────────────────────────────────────────────────────┐
│ TOP BAR  logo · role-scoped nav · search · alerts · account   │  62 px, sticky
├──────────┬───────────────────────────────────────────────────┤
│ SIDEBAR  │  PAGE HEAD   title · subtitle · primary action     │
│ (module  │  ─────────────────────────────────────────────    │
│  sections│  STAT ROW    2–4 tiles: value, label, hint        │
│  for     │  ─────────────────────────────────────────────    │
│  complex │  CONTENT     charts · tables · timelines · forms  │
│  roles)  │  ─────────────────────────────────────────────    │
│          │  PAGINATION / EMPTY STATE                         │
└──────────┴───────────────────────────────────────────────────┘
```
Simple roles (patient) use the top bar alone; dense roles (admin, pharmacy, lab) gain a
left sidebar grouping their module's sections.

## 11.3 Component specifications
**Stat tile** — icon in a tinted square, large value (27 px, 650 weight), label, optional
hint. Tone variants: default, accent, ok, info. Hover raises shadow 1 px.

**Data table** — sticky header, 46 px header / 56 px rows, hover highlight, right-aligned
numerics, status as pills, row actions at the right edge, paginator below. Empty state
replaces the table entirely rather than showing an empty grid.

**Status pill** — 12 px, 600 weight, rounded full, background/foreground pair per state
(PAID/CONFIRMED/SIGNED green; ISSUED/REQUESTED/OPEN amber; CANCELLED/VOID/critical red;
AMENDED blue).

**Empty state** — dashed border, muted icon, one-line title, one-sentence explanation of
what will appear here and a button to the action that creates it. Never a bare "No data".

**Timeline** — vertical rail with dots; used for today's patients, visit history, bed
movements and audit trails.

**Form** — outlined fields, two-column grid collapsing to one below 720 px, inline
validation on blur, hint text under money and clinical fields, actions right-aligned
with the primary last.

**Dialog** — 480 px, title, body, cancel + primary; destructive actions use danger
colouring and require typing confirmation for irreversible operations.

**Live indicator** — pulsing dot next to any element receiving push updates, so users
know the screen is current without refreshing.

## 11.4 Signature screens

**Waiting-room display board** (public, kiosk, 1920×1080): near-black teal background,
"NOW SERVING" with a 132 px token, patient's first name only, next-in-line chips with
estimates, clock, footer explaining that estimates are computed from actual consultation
times. No login, no clinical data, readable across a room.

**Doctor console**: current consultation card with a large token and Complete action;
called-but-absent cards with Recall; the waiting list with token, name, priority chip,
waited-minutes and estimate; one primary "Call next patient" button in the page head.

**Patient queue view** (mobile-first): giant token, ordinal position ("3rd in line"),
estimated minutes with a clock icon, progress bar, doctor name, check-in time, waited
minutes, and a line explaining that estimates shift with the clinic's actual pace.

**Encounter chart**: left — patient banner (name, MRN, age/sex, allergies); centre —
complaint, notes, diagnoses, prescriptions; right — prior visits and results. Signed
records show a lock and an amendment history.

**Bed board** *(Planned)*: ward tabs, bed grid coloured by state (free/occupied/cleaning/
maintenance), patient name and LOS on occupied beds, drag to transfer.

# 12 · Dashboard Description

Every dashboard carries: stat row, at least one chart, an action queue (the work waiting
for *this* user), recent activity, search, filters, pagination and notification access.

| Role | Stats | Charts | Action queue | Recent |
|---|---|---|---|---|
| **Patient** | Upcoming visits · records · outstanding balance · completed visits | — (clarity over analytics) | Complete profile · pay invoice | Next appointments, recent visits |
| **Doctor** | Patients today · requests to review · charts to complete · signed | Weekly patient volume *(Planned)* | Accept/decline requests · unsigned charts | Today's timeline |
| **Reception** | Appointments today · waiting now · walk-ins · collections | Hourly arrival load *(Planned)* | Pending confirmations · unpaid at counter | Live queue |
| **Nurse** *(Planned)* | Assigned patients · vitals due · medication due · critical alerts | Vitals trend per patient | Overdue medication · abnormal vitals | Ward activity |
| **Lab** *(Planned)* | Pending orders · in process · awaiting verification · TAT breaches | TAT distribution | STAT orders · critical results | Recent releases |
| **Pharmacy** *(Planned)* | Prescriptions queued · dispensed today · low stock · expiring | Stock value by category | Dispense queue · reorder alerts | Recent dispensing |
| **Billing** *(Planned)* | Collected today · outstanding · claims pending · refunds | Revenue by department | Unbilled services · claim follow-ups | Recent transactions |
| **Admin** | Appointments today · registered patients · outstanding revenue · collected | Appointment status donut · revenue bars | Doctor applications · pending confirmations · unpaid invoices | Clinic activity |
| **Super Admin** *(Planned)* | Branches · total patients · group revenue · occupancy | Branch comparison | Branch alerts | Privileged actions |

# 13 · Navigation Structure

Navigation is generated from one role table — a user sees only what they act on; links
they cannot use are absent, not disabled.

| Role | Navigation |
|---|---|
| **Visitor** | Find a doctor · Sign in · Register |
| **Patient** | Dashboard · Find a doctor · My queue · Appointments · Records · Invoices · (menu) My profile |
| **Doctor** | My day · Live queue · Charts · (menu) My professional profile, Availability |
| **Reception** | Dashboard · Live queue · Appointments · Patients · Billing counter |
| **Nurse** *(Planned)* | My ward · Vitals · Medication · Handover |
| **Lab** *(Planned)* | Worklist · Sample collection · Results · Verification |
| **Pharmacy** *(Planned)* | Prescription queue · Dispensing · Inventory · Purchases |
| **Billing** *(Planned)* | Bills · Payments · Claims · Refunds · Day close |
| **Admin** | Dashboard · Live queue · Appointments · Patients · Billing · Staff · (menu) Doctor applications, Tariffs, Audit |
| **Super Admin** *(Planned)* | Branches · Global config · Cross-branch analytics · Platform audit |

**Deep-link routes:** `/board/:doctorId` (public kiosk), `/queue`, `/records`,
`/patients/:id`, `/staff/new`, `/doctor-approvals`.

# 14 · Module Explanation

| Module | Purpose | Depends on | Produces |
|---|---|---|---|
| Identity | Accounts, roles, tokens | — | User identity for every request |
| Patient | Demographic master | Identity | MRN, patient events |
| Provider | Doctors, credentials, availability | Identity | Bookable capacity |
| Appointment | Scheduling and lifecycle | Patient, Provider | Confirmed visits, fee snapshots |
| Queue | Live OPD flow | Appointment, Provider | Consultation completion events |
| EMR | Clinical documentation | Queue/Appointment | Diagnoses, prescriptions |
| Laboratory | Diagnostics | EMR | Results, charges |
| Radiology | Imaging | EMR | Reports, charges |
| Pharmacy | Dispensing | EMR, Inventory | Dispensing records, charges |
| Inventory | Stock control | Pharmacy | Availability, expiry control |
| Admission | IPD beds and stay | EMR, Ward master | Occupancy, room charges |
| Nursing | Ward care | Admission, EMR | Vitals, medication records |
| OT | Surgery scheduling | Admission, Provider | Operative records, charges |
| Emergency | Triage-first care | Patient, Queue | Priority cases |
| Billing | Consolidated charging | All charge producers | Invoices, payments |
| Insurance | Payer settlement | Billing | Claims, co-pay splits |
| Notification | Multi-channel messaging | All events | Delivered messages |
| Analytics | Operational intelligence | All events | Dashboards, reports |
| Audit | Immutable trail | All services | Compliance evidence |
| File | Object storage | Lab, Radiology, EMR, Billing | Stored documents, signed URLs |
| Administration | Configuration and staff | Identity, Provider | Tariffs, branches, roles |
