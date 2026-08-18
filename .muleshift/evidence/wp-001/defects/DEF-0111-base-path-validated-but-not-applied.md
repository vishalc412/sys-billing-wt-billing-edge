---
id: DEF-0111
title: "billing.api.base-path is validated, logged, and applied to nothing — the service serves at the root in every profile"
service: billing-edge
severity: medium
class: implementation
raised_at_stage: S5
summary: >
  ADM-004-c asserts the externally visible base path RESOLVES to the deployment's value.
  application-local.yaml sets billing.api.base-path: /sapi-billing/v1 (reproducing the legacy local
  listener path). StartupConfigurationValidator requires it and prints it. Nothing binds it to
  server.servlet.context-path or to any mapping, so the service serves at the root in every profile
  and the configured value is inert: /info answers 200 at the root, and /sapi-billing/v1/info returns
  404. The legacy served at /sapi-billing/v1; the configured base path does not resolve.
reproduction: >
  Against the booted container (S5BootedEdgeIT, local profile, which sets
  billing.api.base-path=/sapi-billing/v1), GET /info returns 200 and GET /sapi-billing/v1/info
  returns 404 {"message":"Resource not found"}. The S5 test asserts both observations and names
  DEF-0111 in its @DisplayName: "ADM-004-c: billing.api.base-path is validated, logged, and applied
  to nothing (DEF-0111)".
evidence:
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#theConfiguredBasePathIsNeverApplied"
  - "evidence:run-s5-billing-edge-8f2eec88"
traces_to:
  - "mule:flow/sapi-billing-search-main"
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#theConfiguredBasePathIsNeverApplied"
  - "evidence:run-s5-billing-edge-8f2eec88"
status: open
blocks_gate: false
triage:
  round: 2
  disposition: >
    Implementation/wiring defect. billing.api.base-path is read, required, logged, and then dropped
    — nothing binds it to server.servlet.context-path or to any path mapping, so the service serves
    at the root in every profile and the configured base path (the legacy /sapi-billing/v1 listener
    path) is inert. ADM-004-c (the externally visible base path resolves to the deployment's value)
    is unsatisfied. This is a routing/deployment divergence from the legacy listener path; no data
    or security exposure. Autonomously fixable by a java-engineer re-dispatch. Round 2 of the
    bounded loop.
  action: >
    Re-dispatch to java-service-engineer (billing-edge): bind billing.api.base-path to
    server.servlet.context-path (or apply it to the request mappings) so the service serves at the
    configured base path — /sapi-billing/v1/info resolves and the bare /info does not (or whatever
    the deployment's value is). Preserve the legacy listener path. The S5 test
    S5BootedEdgeIT#theConfiguredBasePathIsNeverApplied must flip from asserting /info=200 +
    /sapi-billing/v1/info=404 to asserting /sapi-billing/v1/info=200 (the configured base path
    resolves) per ADM-004-c.
  decided_by: migration-architect
---

# DEF-0111: billing.api.base-path is validated, logged, and applied to nothing

Severity:  medium
Found in:  S5 verify of WP-001, billing-edge
Service:   billing-edge
Criterion: ADM-004-c
Layer:     routing / deployment wiring

## What is wrong

The S5 probe `@DisplayName`:

> "ADM-004-c: billing.api.base-path is validated, logged, and applied to nothing (DEF-0111)"

`application-local.yaml` sets `billing.api.base-path: /sapi-billing/v1`, reproducing the legacy local
listener path. `StartupConfigurationValidator` requires it and prints it. Nothing binds it to
`server.servlet.context-path` or to any mapping. So the service serves at the root in every profile:
`/info` answers 200 at the root, and `/sapi-billing/v1/info` returns 404. The configured value is
inert. The test's AssertJ description:

> "ADM-004-c asserts the externally visible base path RESOLVES to the deployment's value. It does
> not resolve to anything: the declared path is not served."

## Severity rationale

**Medium.** A routing divergence from the legacy listener path (`/sapi-billing/v1`); the configured
base path does not resolve. No data or security exposure. It is an operational/deployment-wiring
defect (callers addressed at the legacy path get 404). Not a gate blocker on the S7 HIGH rule.

## Authoritative basis

ADM-004-c (the externally visible base path resolves to the deployment's value). The frozen
contract's `servers` block notes "Externally visible base path is unknown (U12, R-002)" — the
deployment's value must be the one that resolves.

## Disposition

Class: **implementation** (routing/wiring). Re-dispatch to the billing-edge java-service-engineer.
Round 2 of the bounded loop. Status remains **open**.

## Trace chain

`mule:flow/sapi-billing-search-main` (the legacy listener at /sapi-billing/v1) →
`config.StartupConfigurationValidator` (requires + logs billing.api.base-path) → nothing binds it →
`test:S5BootedEdgeIT#theConfiguredBasePathIsNeverApplied` →
`evidence:run-s5-billing-edge-8f2eec88`