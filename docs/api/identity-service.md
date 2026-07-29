# identity-service API

Base path via gateway: `/api/auth`. All endpoints public (gateway allowlist) except where noted. Errors follow RFC 7807 (see [guidelines](guidelines.md)).

## POST /api/auth/register
Self-registration; always creates a PATIENT account (FR-A1). Staff/doctor/admin accounts are provisioned by admins (Phase 3+ admin API).

Request `{"email": "a@b.dev", "password": "Password123"}` — password: 10–72 chars, upper + lower + digit.
`201` → `{"data": {"accessToken", "refreshToken", "userId", "email", "roles"}}`
`400` validation · `409` email already used

## POST /api/auth/login
`200` → same envelope as register.
`401` — identical body for unknown email and wrong password (no user enumeration).

## POST /api/auth/refresh
Request `{"refreshToken": "…"}`. Rotation: the presented token is consumed; a new pair is returned. Replaying a consumed token revokes **all** the user's sessions (theft signal).
`200` → auth envelope · `401` invalid/expired/replayed

## POST /api/auth/logout
Request `{"refreshToken": "…"}`. Idempotent, always `204` — revokes the token server-side.

## Access token claims
`sub` (userId, UUID) · `email` · `roles` (array) · `iat`/`exp` (15 min TTL). HS256; validated at the gateway which forwards `X-User-Id` / `X-User-Roles` to services.

## Curl smoke test (via gateway)
```bash
curl -s -X POST localhost:8080/api/auth/register -H 'Content-Type: application/json' \
  -d '{"email":"pat1@example.dev","password":"Password123"}'
TOKEN=$(curl -s -X POST localhost:8080/api/auth/login -H 'Content-Type: application/json' \
  -d '{"email":"pat1@example.dev","password":"Password123"}' | python -c "import sys,json;print(json.load(sys.stdin)['data']['accessToken'])")
curl -i localhost:8080/api/patients -H "Authorization: Bearer $TOKEN"   # 404 until Phase 3 — but 401 without the token
```
