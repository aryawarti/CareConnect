# patient-service API

Base path via gateway: `/api/patients`. Every endpoint requires a JWT (gateway) plus the role listed. Errors: RFC 7807.

| Method & path | Roles | Purpose |
|---|---|---|
| `GET /api/patients?q=&page=&size=&sort=` | STAFF, ADMIN, DOCTOR | Paged search: name / phone / MRN. Default sort `lastName,asc`, size 20 |
| `POST /api/patients` | STAFF, ADMIN | Register patient; MRN auto-assigned (`P-100001`, DB sequence). 201 + Location |
| `GET /api/patients/{id}` | STAFF, ADMIN, DOCTOR | Fetch one |
| `PUT /api/patients/{id}` | STAFF, ADMIN | Full update |
| `DELETE /api/patients/{id}` | STAFF, ADMIN | **Soft** deactivate (status → INACTIVE); 204. Never hard-deletes (FR-B3) |
| `POST /api/patients/me` | PATIENT | **Self-onboarding**: create own linked profile (once; 409 `profile-exists` after). Publishes `PatientRegistered` |
| `GET /api/patients/me` | PATIENT | Own profile (scoped by userId from the token — structurally IDOR-proof) |
| `PUT /api/patients/me` | PATIENT | Update own **contact** data only; name/DOB are staff-controlled |

Paged response: `{"data": [...], "meta": {"page","size","totalElements","totalPages"}}`.

Ownership model: a `patients.user_id` column links a record to an identity account. Staff-created patients are unlinked until the patient registers; linking flows arrive with the appointment phase.

## Smoke test
```bash
TOKEN=... # login as a STAFF/ADMIN user
curl -s "localhost:8080/api/patients?q=sha" -H "Authorization: Bearer $TOKEN"
curl -s -X POST localhost:8080/api/patients -H "Authorization: Bearer $TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"firstName":"Asha","lastName":"Verma","dateOfBirth":"1990-04-12","gender":"FEMALE","phone":"9876512345"}'
```
Note: your self-registered account has only PATIENT — seed an admin (`SEED_ADMIN_EMAIL`/`SEED_ADMIN_PASSWORD` in `.env`, restart identity-service) to exercise the staff endpoints.
