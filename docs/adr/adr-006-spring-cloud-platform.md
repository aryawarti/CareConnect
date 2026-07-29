# ADR-006: Eureka + Config Server + Spring Cloud Gateway

**Status:** Accepted · 2026-07-18

## Context
Services need to find each other, share configuration, and sit behind one entry point.

## Decision
The standard Spring Cloud triad: Eureka (discovery), Spring Cloud Config (native/git-backed config), Spring Cloud Gateway (edge).

## Alternatives
- **Kubernetes-native** (DNS discovery, ConfigMaps, Ingress) — the modern production answer; rejected because K8s is explicitly out of scope and would bury Spring-level learning under cluster ops.
- **Consul** — discovery + KV + health in one; fine tool, weaker Spring Boot 3 story than the native triad and one more moving part to learn simultaneously.
- **No discovery, static Compose DNS** — genuinely sufficient locally; rejected because client-side load-balancing and registry patterns are core curriculum here.
- **nginx/Traefik edge** — no first-class Spring Security/JWT claim handling; would split edge logic across two technologies.

## Consequences
+ Coherent, well-documented stack; each piece teaches a named pattern.
− Eureka is maintenance-mode software (fact acknowledged in interview notes — the *pattern* transfers to Consul/K8s); three extra processes locally.
