# ADR-005: Stateless JWT validated at the gateway

**Status:** Accepted · 2026-07-18

## Context
Multiple services must authenticate requests without sharing session state.

## Decision
identity-service issues HS256-signed JWTs (15-min access + 7-day hashed, revocable refresh tokens). The gateway validates signature/expiry, strips any inbound `X-User-*` headers, and forwards identity as trusted headers. Services apply role- and ownership-level authorization.

## Alternatives
- **Server-side sessions + Redis** — revocation is trivial, but adds a stateful dependency on every request and defeats statelessness; the classic monolith pattern.
- **Opaque tokens + introspection** — every request hits identity-service: availability and latency coupling on the hottest path.
- **Full OAuth2/OIDC via Keycloak** — production-realistic and standards-correct, but outsources exactly the auth internals this project intends to demonstrate; also heavy. Revisit-worthy as a stretch exercise.
- **Per-service JWT re-validation** (instead of trusted headers) — stronger zero-trust posture, small CPU cost; rejected for v1 since services aren't reachable except via gateway in Compose networking; documented as the hardening upgrade path.

## Consequences
+ Horizontal scale with no session store; auth logic in exactly one place per concern.
− JWTs are irrevocable until expiry → mitigated by 15-min TTL + revocable refresh tokens.
− Trusted-header model depends on network topology (services not directly exposed).
