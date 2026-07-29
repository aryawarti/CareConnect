# 8 · Complete User Stories

Format: **US-<ROLE>-<n>** · story · numbered steps · acceptance criteria (AC).
Steps describe what actually happens in the system, including the automatic effects.

---

## 8.1 Patient

### US-PAT-1 · Registration
*As a person needing care, I want an account so I can book and see my records.*
1. Opens the site, selects **Register**, chooses **I'm a patient**.
2. Enters email and password (10+ chars, upper, lower, digit).
3. Account created with PATIENT role; tokens issued; lands on dashboard.
4. Dashboard shows a prompt: complete your patient profile.
5. Submits name, date of birth, gender, phone, emergency contact.
6. System assigns a permanent MRN and publishes `PatientRegistered`.
7. Welcome notification delivered.

**AC:** duplicate email rejected with 409 · weak password rejected with field-level
errors · MRN unique and sequential · profile creation is one-time (second attempt 409)
· patient can book immediately after step 5.

### US-PAT-2 · Login
1. Enters email and password. 2. Receives access token (15 min) and refresh token (7 days).
3. Lands on the patient dashboard. 4. Session restores silently on reload.
**AC:** wrong password and unknown email return the identical message (no user
enumeration) · disabled accounts are refused with a distinct message · concurrent
refreshes never invalidate the session.

### US-PAT-3 · Forgot password *(Planned)*
1. Requests reset by email. 2. Receives a single-use, time-limited link (30 min).
3. Sets a new password. 4. All existing sessions are revoked.
**AC:** link is single-use · response is identical whether or not the email exists.

### US-PAT-4 · Book an appointment
1. Opens **Find a doctor**; filters by specialty, department or name.
2. Sees only verified, active doctors with fees.
3. Selects a doctor and a date.
4. System shows genuinely free slots (availability − booked − exceptions − past).
5. Selects a slot, adds a reason, confirms.
6. Appointment created as REQUESTED; fee snapshotted; `AppointmentRequested` published.
7. Doctor's dashboard shows the request; patient sees "awaiting confirmation".
8. On acceptance, status becomes CONFIRMED and the patient is notified.

**AC:** two patients booking the same slot — one succeeds, the other receives 409 with a
refreshed slot list · booking outside availability rejected · fee shown equals fee
billed later.

### US-PAT-5 · Cancel
1. Opens **Appointments**, selects an upcoming one, cancels.
2. Allowed only if more than 2 hours remain; otherwise refused with the reason.
3. Slot is freed immediately; doctor and reception see the change; patient notified.
**AC:** cancelling within cutoff returns 409 · a cancelled slot is instantly bookable.

### US-PAT-6 · Reschedule *(Planned)*
1. Chooses reschedule; picks another free slot for the same doctor.
2. Original is CANCELLED with reason "rescheduled"; new appointment linked to it.
**AC:** history preserves both records and the link.

### US-PAT-7 · Arrive and wait
1. Checks in at reception (or self-checks in on arrival).
2. Receives a token; enters the doctor's live queue.
3. **My queue** shows live position, estimated minutes, and current status.
4. Lobby display shows the token being served.
5. On being called, the phone view changes to "Please proceed".
**AC:** position and estimate update within 1 s of any queue change · emergency arrivals
reorder the queue without breaking arrival order within priority groups.

### US-PAT-8 · Medical history
1. Opens **Records**; sees visits newest-first with doctor, date and complaint.
2. Expands one to read notes, diagnoses, prescriptions and any amendments.
3. Lab and radiology reports appear under the visit that ordered them.
**AC:** only own records · unreleased results are not visible · amendments show reason
and prior text.

### US-PAT-9 · Prescriptions
1. Opens a visit; sees medication, dosage, frequency, duration, instructions.
2. Downloads a PDF prescription. *(Planned)*
3. Sees dispensing status from pharmacy. *(Planned)*

### US-PAT-10 · Reports *(Planned)*
1. Notified when a report is released. 2. Views the result with reference ranges and
abnormal flags. 3. Downloads the PDF via a time-limited link.
**AC:** only released, verified reports are visible · download URLs expire.

### US-PAT-11 · Bills and payment
1. Opens **Invoices**; sees issued and paid bills with amounts.
2. Pays an outstanding invoice; a receipt is generated and emailed.
3. Downloads invoice/receipt PDFs. *(Planned)*
**AC:** double-click pays once (idempotency reference) · another patient's invoice is
inaccessible even by direct id.

### US-PAT-12 · Admission *(Planned)*
1. Doctor advises admission; patient consents.
2. Bed allocated; patient and family see ward, bed and attending doctor.
3. Daily charges accrue and are visible as they accumulate.

### US-PAT-13 · Discharge *(Planned)*
1. Doctor issues discharge; summary generated with diagnosis, course, medication, follow-up.
2. Final bill consolidates all charges; deposit adjusted.
3. On settlement, discharge completes; bed released for cleaning; summary downloadable.

### US-PAT-14 · Notifications
Receives: registration welcome, request received, appointment confirmed/declined,
reminders *(Planned)*, called to consultation, report ready, invoice issued, payment
received. **AC:** every notification is idempotent; one event yields one message.

### US-PAT-15 · Feedback *(Planned)*
After a completed visit, rates the experience and comments; results feed doctor and
department quality reports.

### US-PAT-16 · Emergency request *(Planned)*
Uses **Emergency** to alert the hospital; triage is created before full registration.

### US-PAT-17 · Profile & insurance
Updates contact details and emergency contact; adds insurer, policy number and validity
*(Planned)*; clinical identity fields remain staff-controlled.

---

## 8.2 Doctor

### US-DOC-1 · Join the hospital
**Path A — self-registration:** registers choosing **I'm a doctor** → submits
qualification, medical registration number, experience, specialty, department, fee, bio →
profile is PENDING → administration verifies → APPROVED → appears in the directory.
**Path B — hired:** administrator creates the account and profile in one flow and hands
over credentials; profile is APPROVED on creation.
**AC:** a PENDING doctor is invisible to patients and cannot receive bookings · a
REJECTED doctor sees the reason · registration number is captured for verification.

### US-DOC-2 · Login and dashboard
Sees, at a glance: patients today, requests awaiting decision, charts to complete,
signed records; then the request inbox, today's timeline, and unsigned charts.

### US-DOC-3 · Accept or decline requests
1. Sees each request with patient, time and reason.
2. **Accept** → CONFIRMED, patient notified. **Decline** → slot freed, patient notified.
**AC:** a doctor can only act on their own appointments (server-verified) · declining
frees the slot immediately.

### US-DOC-4 · Manage availability
Sets weekly windows and slot length; adds leave exceptions; changes apply to future
booking immediately and never invalidate existing appointments.

### US-DOC-5 · Run the live queue
1. Opens **Live queue**; sees waiting patients in fairness order with wait times.
2. **Call next** → token announced on the lobby board and the patient's phone.
3. **Recall** if absent; third unanswered call auto-skips.
4. **Start** when the patient enters; **Complete** when finished.
5. Completion drives: appointment COMPLETED, chart opened, invoice issued, patient notified.
**AC:** every connected screen updates within 1 s · consultation duration is recorded and
feeds future wait estimates.

### US-DOC-6 · Review patient history before consulting
Opens the patient's longitudinal record: previous encounters, diagnoses, active
medication, allergies *(Planned)*, recent lab and imaging results.

### US-DOC-7 · Document the encounter
1. Opens the chart created by the completed consultation.
2. Records chief complaint and clinical notes.
3. Adds diagnoses with ICD-10 codes.
4. Adds prescriptions (medication, dosage, frequency, duration, instructions).
5. **Signs** the encounter — it becomes immutable.
6. Signing queues the prescription for pharmacy *(Planned)*.
**AC:** signing requires notes · a signed chart cannot be edited, only amended with a
reason · amendments preserve prior text.

### US-DOC-8 · Order investigations *(Planned)*
1. Selects tests/imaging from the catalogue within the encounter.
2. Marks urgency (routine/urgent/STAT) and clinical indication.
3. Order is billable immediately and appears in the lab/radiology worklist.
4. Doctor is alerted when results are released; critical values alert immediately.

### US-DOC-9 · Admit a patient *(Planned)*
Requests admission with provisional diagnosis, ward type and estimated stay; reception
allocates a bed; the patient appears in the doctor's inpatient list for daily rounds.

### US-DOC-10 · Discharge *(Planned)*
Writes the discharge summary (diagnosis, course, procedures, medication, follow-up);
discharge is released once billing clears.

### US-DOC-11 · Surgery *(Planned)*
Schedules a procedure with theatre, team and duration; completes pre-op checklist;
records operative notes and consumables used.

### US-DOC-12 · Certificates *(Planned)*
Issues fitness/sick-leave certificates as signed PDFs, recorded against the encounter.

---

## 8.3 Receptionist

### US-REC-1 · Register a walk-in
Captures demographics in under a minute; MRN issued; patient exists for booking and queueing.
### US-REC-2 · Book on behalf of a patient
Searches the patient, picks doctor/date/slot, books, and may confirm directly for phone bookings.
### US-REC-3 · Check-in
Finds today's appointment, checks the patient in, issues the token, sets triage priority when needed.
### US-REC-4 · Register a walk-in straight into a queue
No appointment: selects doctor, complaint and priority; token issued immediately.
### US-REC-5 · Manage the day
Monitors clinic-wide queues, confirms pending requests, reschedules, marks no-shows,
handles patients who leave.
### US-REC-6 · Collect payment
Takes payment at the counter, issues receipt, reconciles the counter at shift end *(Planned)*.
### US-REC-7 · Admission and discharge support *(Planned)*
Allocates beds, collects deposits, completes discharge paperwork once billing clears.

---

## 8.4 Nurse *(Planned module)*

### US-NUR-1 · Shift start
Sees only patients on the assigned ward/shift, with acuity and pending tasks.
### US-NUR-2 · Vitals rounds
Records temperature, pulse, BP, respiration, SpO2, pain score; abnormal values are
highlighted and alert the doctor.
### US-NUR-3 · Medication administration
Works the due list; records given/missed/refused with time and reason; the MAR is
attributable and immutable.
### US-NUR-4 · Doctor's orders
Sees new orders in real time; acknowledges and executes ward tasks.
### US-NUR-5 · Handover
Generates a shift summary per patient: condition, events, pending tasks.

---

## 8.5 Lab Technician *(Planned module)*

### US-LAB-1 · Worklist
Sees ordered tests by priority and TAT risk, STAT first.
### US-LAB-2 · Sample collection
Prints a barcode/accession label; scanning binds sample to patient and order — the
control that prevents mis-identification.
### US-LAB-3 · Processing
Scans the sample to move it to IN_PROCESS; rejects unsuitable samples with a reason and
triggers re-collection.
### US-LAB-4 · Result entry
Enters values per analyte; system flags out-of-range against reference data; critical
values raise an immediate alert to the ordering doctor.
### US-LAB-5 · Verification and release
A senior verifies before release; the PDF is stored; doctor and patient are notified.

---

## 8.6 Pharmacist *(Planned module)*

### US-PHR-1 · Prescription queue
Sees prescriptions from signed encounters, newest first, with patient and doctor.
### US-PHR-2 · Safety check
System flags interactions, allergies and duplicate therapy before dispensing.
### US-PHR-3 · Dispense
Selects batch by earliest expiry (FEFO), records quantity; stock decrements; charge
flows to the patient's bill; partial dispensing and substitution are recorded with reason.
### US-PHR-4 · Counter sale
Sells OTC items without a prescription; bill raised at the counter.
### US-PHR-5 · Stock control
Monitors reorder alerts and expiry (90/60/30 days); raises purchase orders; receives
goods against them.
### US-PHR-6 · Controlled substances
Maintains the statutory register with mandatory audit fields.

---

## 8.7 Billing Executive

### US-BIL-1 · Consolidated bill *(Planned)*
Sees every charge for a patient episode — consultation, lab, radiology, pharmacy, room,
procedures — itemised with tax.
### US-BIL-2 · Collect payment
Records payment by mode with idempotent reference; issues receipt.
### US-BIL-3 · Discounts *(Planned)*
Applies within authority limits, with reason; beyond the limit requires approval.
### US-BIL-4 · Insurance *(Planned)*
Links the policy, splits payer vs co-pay by plan rules, submits the claim packet, tracks
status through to settlement.
### US-BIL-5 · Refunds *(Planned)*
Raises a refund request with reason; administrator approves; ledger and audit updated.
### US-BIL-6 · Day close *(Planned)*
Reconciles collections by counter and mode; flags variances.

---

## 8.8 Administrator

### US-ADM-1 · Onboard staff
Creates the account, role, professional profile and schedule in one flow; hands over
temporary credentials.
### US-ADM-2 · Verify doctor applications
Reviews qualification and registration number; approves (doctor becomes bookable) or
rejects with a reason the applicant can see.
### US-ADM-3 · Manage access
Revokes access for departed staff (sessions end immediately; records remain attributed),
restores it, resets passwords.
### US-ADM-4 · Departments and wards *(wards Planned)*
Maintains departments; defines wards, bed counts and room tariffs.
### US-ADM-5 · Tariffs *(Planned)*
Maintains service prices per branch: consultation, lab, radiology, room, procedures.
### US-ADM-6 · Inventory oversight *(Planned)*
Approves purchase orders, reviews stock value, expiry exposure and consumption trends.
### US-ADM-7 · Revenue and analytics *(Partial)*
Reviews collections, outstanding, department revenue, occupancy, wait times, no-shows.
### US-ADM-8 · Audit *(Planned)*
Searches the immutable log by user, patient, entity or period; exports for auditors.
### US-ADM-9 · Configuration *(Planned)*
Sets cancellation cutoffs, slot defaults, tax rates, notification templates.

---

## 8.9 Super Admin *(Planned)*

### US-SAD-1 · Branch provisioning
Creates a branch, seeds its departments and tariffs, appoints its administrator.
### US-SAD-2 · Global configuration
Manages roles/permissions, retention policy and integration credentials across branches.
### US-SAD-3 · Cross-branch analytics
Compares occupancy, revenue, wait times and clinical volumes across branches.
### US-SAD-4 · Platform audit
Reviews privileged actions across all branches.
