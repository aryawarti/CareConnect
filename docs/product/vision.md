# Project Vision

## What CareConnect is

CareConnect is the management platform for a small **outpatient clinic network**: patients register and book appointments with doctors; staff manage schedules; doctors record encounter notes and prescriptions; the clinic bills for completed visits and keeps everyone informed with notifications.

Scoping to *outpatient clinics* (not a full hospital) is deliberate: it keeps every workflow end-to-end realistic while avoiding domains (inpatient wards, surgery, insurance adjudication, HL7/FHIR interop) that would each be a project of their own.

## Who uses it

| Role | Needs |
|---|---|
| **Patient** | Register, manage profile, book/cancel appointments, see visit history, invoices, notifications |
| **Doctor** | Manage availability, see schedule, record encounters/diagnoses/prescriptions |
| **Staff** | Manage patients & providers, handle scheduling exceptions, manage billing |
| **Admin** | User & role administration, system oversight |

## What the project must demonstrate

1. Sensible service boundaries derived from the business domain (not "one service per table").
2. Both synchronous (REST/OpenFeign) and asynchronous (Kafka) inter-service communication, each used where appropriate.
3. Production-grade practices at a scale a 1–2 year backend engineer credibly owns: stateless JWT auth, database-per-service, migrations, resilience patterns, structured errors, testing strategy, observability basics.
4. Documentation discipline: every decision has a recorded rationale.

## Explicit non-goals

Real medical-device or EHR compliance (HIPAA certification, FHIR), payment-gateway integration, high-availability multi-region deployment, mobile apps. These are acknowledged in docs where relevant, but not built.
