#!/usr/bin/env python3
"""
Seeds a realistic, populated clinic through the PUBLIC API.

Why through the API and not SQL: every record then passes real validation and
authorization, and completing appointments fires the real event chain — so the
encounters, invoices and notifications that appear were produced by the system,
not inserted behind its back.

Idempotent: if the marker patient already exists, it exits without doing
anything, so restarting the stack doesn't duplicate the clinic.

Standard library only (runs on python:3.12-alpine, no pip install).
"""
import json
import os
import sys
import time
import urllib.error
import urllib.request
from datetime import date, datetime, timedelta, timezone

GATEWAY = os.environ.get("GATEWAY_URL", "http://api-gateway:8080")
ADMIN_EMAIL = os.environ.get("SEED_ADMIN_EMAIL", "admin@careconnect.local")
ADMIN_PASSWORD = os.environ.get("SEED_ADMIN_PASSWORD", "Admin12345")
DOCTOR_PASSWORD = os.environ.get("SEED_DOCTOR_PASSWORD", "Doctor12345")
PATIENT_PASSWORD = os.environ.get("SEED_PATIENT_PASSWORD", "Patient12345")
MARKER_EMAIL = "asha.verma@careconnect.demo"   # presence => already seeded


def call(method, path, body=None, token=None, expect_error=False, attempts=3):
    url = f"{GATEWAY}{path}"
    data = json.dumps(body).encode() if body is not None else None
    for attempt in range(1, attempts + 1):
        request = urllib.request.Request(url, data=data, method=method)
        request.add_header("Content-Type", "application/json")
        if token:
            request.add_header("Authorization", f"Bearer {token}")
        try:
            with urllib.request.urlopen(request, timeout=90) as response:
                raw = response.read().decode()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:
            if expect_error:
                return None
            # 5xx on a cold JVM (first hit on a code path can trip a
            # Resilience4j timeout) is worth one retry; 4xx is a real
            # rejection (bad request, conflict, auth) — never retry those.
            if e.code >= 500 and attempt < attempts:
                print(f"  ! {method} {path} -> {e.code}, retrying...", flush=True)
                time.sleep(3 * attempt)
                continue
            print(f"  ! {method} {path} -> {e.code}: {e.read().decode()[:300]}", flush=True)
            raise
        except (TimeoutError, urllib.error.URLError) as e:
            if attempt < attempts:
                print(f"  ! {method} {path} -> {e}, retrying...", flush=True)
                time.sleep(3 * attempt)
                continue
            raise


def wait_for_gateway(minutes=12):
    # On a cold start every JVM is registering with Eureka at once, so
    # provider-service (which answers this probe) can take several minutes to
    # become reachable through the gateway. Wait generously rather than aborting
    # the seed and leaving an empty clinic.
    print("Waiting for the platform to be ready...", flush=True)
    deadline = time.time() + minutes * 60
    while time.time() < deadline:
        try:
            call("GET", "/api/providers/departments")
            print("  platform is up", flush=True)
            return
        except Exception:
            time.sleep(5)
    sys.exit("Gateway/services never became ready — is the stack healthy?")


def login(email, password):
    return call("POST", "/api/auth/login", {"email": email, "password": password})["data"]


def already_seeded():
    try:
        login(MARKER_EMAIL, PATIENT_PASSWORD)
        return True
    except Exception:
        return False


def next_weekday(offset_days=1):
    day = date.today() + timedelta(days=offset_days)
    while day.weekday() >= 5:          # skip Sat/Sun
        day += timedelta(days=1)
    return day


DOCTORS = [
    ("Nisha", "Rao", "Cardiology", "Cardiology", 900),
    ("Arjun", "Mehta", "General Medicine", "General Medicine", 500),
    ("Priya", "Nair", "Pediatrics", "Pediatrics", 650),
    ("Vikram", "Shah", "Orthopedics", "Orthopedics", 850),
    ("Ananya", "Iyer", "Dermatology", "Dermatology", 700),
]

PATIENTS = [
    ("Asha", "Verma", "1990-04-12", "FEMALE", "9876500001", MARKER_EMAIL),
    ("Rahul", "Sharma", "1985-09-02", "MALE", "9876500002", "rahul.sharma@careconnect.demo"),
    ("Meena", "Iyer", "1978-01-25", "FEMALE", "9876500003", "meena.iyer@careconnect.demo"),
    ("Karan", "Gupta", "2001-06-30", "MALE", "9876500004", "karan.gupta@careconnect.demo"),
    ("Sofia", "Dsouza", "1995-11-08", "FEMALE", "9876500005", "sofia.dsouza@careconnect.demo"),
    ("Imran", "Khan", "1968-03-17", "MALE", "9876500006", "imran.khan@careconnect.demo"),
]

VISITS = [
    ("Fever and sore throat for three days",
     "Temp 101.2F. Throat erythematous, no exudate. Chest clear. Hydration adequate.",
     [("J06.9", "Acute upper respiratory infection, unspecified")],
     [("Paracetamol", "500mg", "Three times daily", 3, "After food"),
      ("Cetirizine", "10mg", "Once at night", 5, "")]),
    ("Follow-up for blood pressure",
     "BP 138/86, down from 148/94. Tolerating medication well. Advised low-salt diet.",
     [("I10", "Essential (primary) hypertension")],
     [("Amlodipine", "5mg", "Once daily", 30, "Morning, with water")]),
    ("Persistent lower back pain after lifting",
     "Lumbar tenderness L4-L5, no radiation, straight-leg raise negative. Advised physiotherapy.",
     [("M54.5", "Low back pain")],
     [("Ibuprofen", "400mg", "Twice daily", 5, "After meals")]),
    ("Itchy rash on forearms",
     "Erythematous papular rash, bilateral forearms. Likely contact dermatitis.",
     [("L23.9", "Allergic contact dermatitis, unspecified cause")],
     [("Hydrocortisone cream", "1%", "Twice daily", 7, "Apply thinly")]),
]


def main():
    wait_for_gateway()

    if already_seeded():
        print("Demo clinic already present — nothing to do.", flush=True)
        return

    print("Signing in as admin...", flush=True)
    admin = login(ADMIN_EMAIL, ADMIN_PASSWORD)
    admin_token = admin["accessToken"]

    departments = {d["name"]: d["id"]
                   for d in call("GET", "/api/providers/departments", token=admin_token)["data"]}

    # ---- doctors: account + profile + weekly availability -------------------
    doctors = []
    for first, last, specialty, department, fee in DOCTORS:
        email = f"dr.{last.lower()}@careconnect.demo"
        user = call("POST", "/api/users", {
            "email": email, "password": DOCTOR_PASSWORD, "roles": ["DOCTOR"]
        }, admin_token)
        doctor = call("POST", "/api/providers/doctors", {
            "firstName": first, "lastName": last, "specialty": specialty,
            "departmentId": departments.get(department, next(iter(departments.values()))),
            "consultationFee": fee, "email": email, "userId": user["data"]["id"],
        }, admin_token)["data"]

        slots = []
        for weekday in range(1, 6):                     # Mon-Fri
            slots.append({"dayOfWeek": weekday, "startTime": "09:00:00",
                          "endTime": "13:00:00", "slotMinutes": 30})
            slots.append({"dayOfWeek": weekday, "startTime": "14:00:00",
                          "endTime": "17:00:00", "slotMinutes": 30})
        call("PUT", f"/api/providers/doctors/{doctor['id']}/availability",
             {"slots": slots}, admin_token)
        doctors.append({**doctor, "email": email})
        print(f"  doctor: Dr. {first} {last} ({specialty})", flush=True)

    # One doctor who applied on their own and is still awaiting verification,
    # so the administrator's approval queue is not empty in a demo.
    applicant_email = "dr.kapoor@careconnect.demo"
    applicant = call("POST", "/api/auth/register",
                     {"email": applicant_email, "password": DOCTOR_PASSWORD,
                      "role": "DOCTOR"})["data"]
    call("POST", "/api/providers/apply", {
        "firstName": "Rohan", "lastName": "Kapoor", "specialty": "Neurology",
        "departmentId": next(iter(departments.values())),
        "qualification": "DM Neurology, AIIMS",
        "registrationNo": "MCI-889231", "experienceYears": 8,
        "bio": "Movement disorders and epilepsy. Speaks Hindi, English and Punjabi.",
        "consultationFee": 1100, "phone": "9876511111",
    }, applicant["accessToken"])
    print("  applicant awaiting approval: Dr. Rohan Kapoor (Neurology)", flush=True)

    # ---- patients: account + profile ---------------------------------------
    patients = []
    for first, last, dob, gender, phone, email in PATIENTS:
        auth = call("POST", "/api/auth/register",
                    {"email": email, "password": PATIENT_PASSWORD})["data"]
        profile = call("POST", "/api/patients/me", {
            "firstName": first, "lastName": last, "dateOfBirth": dob, "gender": gender,
            "phone": phone, "email": email,
            "emergencyContactName": "Emergency contact",
            "emergencyContactPhone": "9876599999",
        }, auth["accessToken"])["data"]
        patients.append({**profile, "email": email, "token": auth["accessToken"]})
        print(f"  patient: {first} {last} ({profile['patientNumber']})", flush=True)

    # ---- appointments in every lifecycle state ------------------------------
    def book(patient, doctor, day, slot_index=0, reason="Consultation"):
        slots = call("GET",
                     f"/api/appointments/available?doctorId={doctor['id']}&date={day}",
                     token=patient["token"])["data"]
        if len(slots) <= slot_index:
            return None
        return call("POST", "/api/appointments", {
            "doctorId": doctor["id"], "startAt": slots[slot_index]["startAt"], "reason": reason
        }, patient["token"])["data"]

    today = date.today().isoformat()
    tomorrow = next_weekday(1).isoformat()
    later = next_weekday(4).isoformat()

    print("Booking appointments...", flush=True)
    completed = []

    # Four visits taken all the way through LIVE CARE FLOW: booked ->
    # confirmed -> checked in -> called -> consulted -> completed. Completing
    # in the queue is what fires AppointmentCompleted, so the encounters and
    # invoices below are produced by the real event chain, not by a shortcut.
    for i, (reason, _notes, _dx, _rx) in enumerate(VISITS):
        appointment = book(patients[i], doctors[i % len(doctors)], today, i, reason)
        if not appointment:
            continue
        call("POST", f"/api/appointments/{appointment['id']}/confirmation", {}, admin_token)
        entry = call("POST", "/api/queue/check-in", {
            "appointmentId": appointment["id"],
            "patientId": appointment["patientId"],
            "doctorId": appointment["doctorId"],
            "complaint": reason,
            "priority": "NORMAL",
        }, admin_token)
        if entry:
            entry_id = entry["data"]["id"]
            call("POST", f"/api/queue/{entry_id}/start", {}, admin_token)
            call("POST", f"/api/queue/{entry_id}/complete", {}, admin_token)
        completed.append((appointment, doctors[i % len(doctors)], VISITS[i]))

    # A live queue for the demo: three patients waiting right now on doctor 0,
    # one of them an emergency, so the board and console have something to show.
    live_doctor = doctors[0]
    for index, (patient, priority, complaint) in enumerate([
            (patients[4], "NORMAL", "Cough and mild fever"),
            (patients[5], "EMERGENCY", "Chest pain, shortness of breath"),
            (patients[3], "URGENT", "Deep cut on hand, bleeding controlled")]):
        call("POST", "/api/queue/walk-in", {
            "patientId": patient["id"], "doctorId": live_doctor["id"],
            "patientName": f"{patient['firstName']} {patient['lastName']}",
            "doctorName": f"Dr. {live_doctor['firstName']} {live_doctor['lastName']}",
            "complaint": complaint, "priority": priority,
        }, admin_token)

    # confirmed (upcoming) appointments
    for i in range(3):
        appointment = book(patients[i + 1], doctors[(i + 2) % len(doctors)], tomorrow, i + 4,
                           "Follow-up consultation")
        if appointment:
            call("POST", f"/api/appointments/{appointment['id']}/confirmation", {}, admin_token)

    # pending requests (awaiting staff confirmation)
    for i in range(2):
        book(patients[i + 3], doctors[i], later, i + 2, "New complaint")

    # one cancelled, to show the state exists
    cancelled = book(patients[5], doctors[4], later, 6, "Routine check-up")
    if cancelled:
        call("POST", f"/api/appointments/{cancelled['id']}/cancellation", {}, admin_token)

    # ---- clinical documentation (as the treating doctor) --------------------
    print("Writing clinical notes...", flush=True)
    time.sleep(6)   # let the outbox relay + consumers create encounters/invoices

    for i, (appointment, doctor, (reason, notes, diagnoses, prescriptions)) in enumerate(completed):
        doctor_auth = login(doctor["email"], DOCTOR_PASSWORD)
        doctor_token = doctor_auth["accessToken"]
        encounters = call("GET", "/api/records/doctor/me", token=doctor_token)["data"]
        match = next((e for e in encounters if e["appointmentId"] == appointment["id"]), None)
        if not match:
            continue
        encounter_id = match["id"]
        call("PUT", f"/api/records/{encounter_id}",
             {"chiefComplaint": reason, "notes": notes}, doctor_token)
        for code, description in diagnoses:
            call("POST", f"/api/records/{encounter_id}/diagnoses",
                 {"code": code, "description": description}, doctor_token)
        for medication, dosage, frequency, days, instructions in prescriptions:
            call("POST", f"/api/records/{encounter_id}/prescriptions", {
                "medication": medication, "dosage": dosage, "frequency": frequency,
                "durationDays": days, "instructions": instructions
            }, doctor_token)
        call("POST", f"/api/records/{encounter_id}/signature", {}, doctor_token)

    # ---- pay some invoices, leave others outstanding ------------------------
    print("Settling some invoices...", flush=True)
    for index, patient in enumerate(patients[:2]):
        invoices = call("GET", "/api/invoices/me", token=patient["token"])["data"]
        for invoice in invoices:
            if invoice["status"] == "ISSUED":
                # A 409 here means the idempotency guard did its job (already
                # paid, e.g. re-running the seeder) — that's the goal either
                # way, so it's not a failure.
                call("POST", f"/api/invoices/{invoice['id']}/payments", {
                    "amount": invoice["amount"], "method": "SIMULATED",
                    "reference": f"seed-{invoice['invoiceNumber']}"
                }, patient["token"], expect_error=True)

    print("\nDemo clinic ready.", flush=True)
    print(f"  Admin   : {ADMIN_EMAIL} / {ADMIN_PASSWORD}", flush=True)
    print(f"  Doctor  : dr.rao@careconnect.demo / {DOCTOR_PASSWORD}", flush=True)
    print(f"  Patient : {MARKER_EMAIL} / {PATIENT_PASSWORD}", flush=True)


if __name__ == "__main__":
    main()
