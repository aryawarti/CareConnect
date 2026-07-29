# provider-service API

Base path via gateway: `/api/providers`. Errors: RFC 7807.

## Public (no JWT — gateway allowlisted, FR-C3)
| Endpoint | Purpose |
|---|---|
| `GET /api/providers/directory?q=&page=&size=` | Active-doctor directory; `q` matches name or specialty |
| `GET /api/providers/departments` | Department list |

## Management
| Endpoint | Roles | Purpose |
|---|---|---|
| `POST /api/providers/doctors` | STAFF, ADMIN | Create doctor (optional `userId` links a DOCTOR identity account) |
| `GET /api/providers/doctors/{id}` | STAFF, ADMIN, DOCTOR | Fetch one |
| `PUT /api/providers/doctors/{id}` | STAFF, ADMIN | Update |
| `DELETE /api/providers/doctors/{id}` | STAFF, ADMIN | Soft deactivate |
| `GET /api/providers/me` | DOCTOR | Own profile |

## Availability & exceptions
| Endpoint | Roles | Notes |
|---|---|---|
| `GET /doctors/{id}/availability` | any authenticated | Weekly windows, ISO day 1=Mon |
| `PUT /doctors/{id}/availability` | STAFF/ADMIN **or owning doctor** | **Replace-all** semantics: send the full weekly schedule. Overlaps → 409 `availability-conflict`; failed replace rolls back (old schedule survives) |
| `GET/POST/DELETE /doctors/{id}/exceptions` | STAFF/ADMIN or owner | Days off despite the weekly schedule |

Ownership rule: role DOCTOR without staff privileges may only touch the schedule whose `doctors.user_id` equals their token `sub` — enforced in the service layer, tested in `ProviderServiceIntegrationTest`.

## identity-service addition (FR-A1)
`POST /api/users` (ADMIN): `{"email","password","roles":["DOCTOR"]}` → 201. Workflow: admin provisions the account, takes the returned `userId`, and enters it when creating the doctor — linking login to profile.
