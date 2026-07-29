# 1 · Introduction

## 1.1 Purpose
CareConnect is a hospital management platform for multi-department, multi-branch
healthcare providers. It manages the complete patient journey — registration,
appointment, consultation, diagnostics, pharmacy, admission, nursing care, billing and
discharge — as one continuous clinical and financial record rather than a set of
disconnected departmental tools.

## 1.2 Intended audience
Hospital administrators and owners; clinical staff (doctors, nurses, technicians);
operational staff (reception, pharmacy, billing); the engineering team building and
operating the system; and auditors who must reconstruct what happened and when.

## 1.3 The problem
A mid-size hospital typically runs five or more unconnected systems: an appointment
book, a paper or spreadsheet patient register, a lab information system, pharmacy
software, and a billing package. The consequences are consistent and expensive:

- **Nothing reconciles.** A completed consultation may never reach billing; a lab test
  is performed but never charged. Revenue leakage of 3–8% is common.
- **The patient record is fragmented.** A doctor cannot see yesterday's lab result
  without a phone call, so tests are repeated.
- **Patients wait blind.** The single largest driver of patient dissatisfaction in
  outpatient care is not the wait itself but not knowing how long it will be.
- **No operational truth.** Management cannot answer "how many patients did we see, how
  long did they wait, what did we earn, what is unbilled" without manual collation.

## 1.4 The solution
One system where every clinical and financial event is a consequence of a real-world
action, propagated to every department that needs it, in real time:

> A doctor marks a consultation complete. Within a second: the clinical chart opens for
> documentation, a bill line is raised, the pharmacy sees the prescription queued, the
> patient's phone shows the updated status, and management's numbers move.

## 1.5 Definitions
| Term | Meaning |
|---|---|
| **OPD** | Outpatient Department — visits without admission |
| **IPD** | Inpatient Department — admitted patients occupying beds |
| **EMR** | Electronic Medical Record — the clinical chart |
| **Encounter** | One clinical interaction between patient and clinician |
| **MRN / UHID** | Medical Record Number — a patient's permanent hospital identifier |
| **Token** | A patient's position marker in a live queue |
| **Formulary** | The hospital's approved list of medicines |
| **TAT** | Turnaround Time — order placed to result available |
| **LOS** | Length of Stay — admission to discharge |
| **Saga** | A sequence of local transactions coordinated by events |

---

# 2 · Business Requirements

## 2.1 Business objectives
| ID | Objective | Measure of success |
|---|---|---|
| BO-1 | Eliminate revenue leakage | 100% of completed clinical services produce a bill line automatically |
| BO-2 | Reduce patient waiting anxiety | Every waiting patient sees a live position and a data-derived time estimate |
| BO-3 | Single patient record | Any authorised clinician retrieves a patient's full history in under 3 seconds |
| BO-4 | Operational visibility | Management sees live occupancy, queue load, TAT and revenue without manual reports |
| BO-5 | Auditability | Every clinical and financial change is attributable to a person and a time, permanently |
| BO-6 | Staff efficiency | Reduce clicks-per-consultation; a doctor documents a routine visit in under 90 seconds |
| BO-7 | Multi-branch scale | One deployment serves multiple hospital branches with isolated data and shared administration |

## 2.2 Business drivers
- **Regulatory.** Clinical records must be retained, attributable and non-destructively
  amendable. Prescriptions and controlled substances require dispensing traceability.
- **Financial.** Departments generate charges independently; without a consolidated bill
  the hospital under-collects and patients dispute invoices.
- **Clinical safety.** Drug interactions, allergies and duplicate orders must be caught
  at the point of ordering, not after dispensing.
- **Competitive.** Patients increasingly choose providers offering online booking,
  digital reports and transparent waiting.

## 2.3 Stakeholders
| Stakeholder | Primary interest | Success looks like |
|---|---|---|
| Patient | Access, clarity, dignity | Books in under a minute, knows the wait, gets reports on a phone |
| Doctor | Clinical efficiency | Full history at hand, minimal documentation friction |
| Nurse | Safe ward routine | Clear assignment list, vitals and medication schedules that prompt |
| Receptionist | Throughput | Registers a walk-in in under 60 seconds, queue self-manages |
| Lab / Radiology technician | Sample integrity | Barcoded samples, no mis-identification, TAT visible |
| Pharmacist | Stock accuracy and safety | Digital prescriptions, interaction warnings, live stock |
| Billing executive | Complete capture | Every service charged; insurance claims tracked |
| Administrator | Control | Staff, departments, tariffs, permissions, audit |
| Super Admin | Multi-branch governance | Branch provisioning, global configuration, cross-branch analytics |
| Auditor / Regulator | Traceability | Immutable log of who did what, when, to which record |

---

# 3 · Scope

## 3.1 In scope
**Clinical:** OPD consultations, live queue management, EMR with diagnoses and
prescriptions, laboratory and radiology ordering and reporting, inpatient admission,
ward and bed management, nursing vitals and medication administration, operation theatre
scheduling, emergency triage, discharge summaries.

**Operational:** patient registration (online and walk-in), appointment scheduling with
doctor acceptance, staff onboarding and credential verification, pharmacy dispensing,
inventory with expiry and reorder control, multi-branch configuration.

**Financial:** consolidated billing across all departments, insurance claims and
co-payments, discounts, taxes, refunds, payment collection and receipts.

**Platform:** authentication with RBAC, notifications (email/SMS/in-app), file and
report storage, analytics dashboards, immutable audit trail, observability.

## 3.2 Out of scope (explicitly, with reasons)
| Excluded | Reason |
|---|---|
| Full HL7 v2 / FHIR interoperability | Requires certification and partner systems; the data model is FHIR-alignable but conformance is a separate programme |
| Medical device integration (PACS/DICOM viewers, monitors) | Hardware-specific; radiology stores reports and image references, not a DICOM viewer |
| Payroll and full HRMS | Adjacent domain; staff records here cover access and clinical assignment only |
| National insurance claim gateway integration | Country-specific; claims are modelled and exportable, gateway adapters are pluggable |
| Telemedicine video | Third-party SDK territory; appointment model reserves a `visitType` for it |
| Real payment gateway settlement | Payments are modelled with idempotent references; a gateway adapter slots in without domain change |

## 3.3 Assumptions
Users have modern browsers; branches have reliable local networking; clinical staff are
licensed and credential-verified by administration; the hospital defines its own tariff
and formulary; time zone is per branch.

## 3.4 Constraints
Data residency within the operating country; clinical records retained per local law
(commonly 7+ years, 21 years for minors); the system must degrade safely — a failure in
billing or notifications must never block clinical care.
