# ADR-001: Single repository (monorepo)

**Status:** Accepted · 2026-07-18

## Context
10 deployable units (7 business, 3 platform services, 1 frontend) built by a "team" of one, showcased as a single portfolio artifact.

## Decision
One repository: `backend/<service>` Maven modules, `frontend/`, shared `docs/`, one `docker-compose.yml`.

## Alternatives
- **Repo per service** — real polyrepo hygiene (independent versioning/CI), but 11 repos to clone, cross-cutting changes become multi-repo PRs, and reviewers can't see the system in one place. Wrong trade for a portfolio and a solo maintainer.
- **Monorepo with shared code library** — tempting (`common-lib` for DTO envelopes etc.). Deliberately minimized: shared libraries couple service release cycles. Only a tiny `platform-starter` (logging/error conventions) is permitted, and only when duplication actually hurts.

## Consequences
+ Atomic cross-service changes; single source of truth for docs; one-command startup.
− Doesn't demonstrate polyrepo CI orchestration (acceptable; noted in interview notes).
