---
id: DEF-0103
title: "The API documentation console cannot serve the contract from a booted service — /console returns 404"
service: billing-edge
severity: medium
class: implementation
raised_at_stage: S5
summary: >
  CON-001-a asserts the API's own published contract is returned as browsable documentation at
  /console. The contract is not packaged into the artifact and billing.console.contract-location
  defaults to file:contracts/billing-edge/openapi.yaml — a path relative to the process working
  directory. ConsoleController therefore falls to notFound() in every environment where the process
  is not started from the repository root, and /console returns 404. The capability is broken in
  every non-production environment (production disables the console per ADR-0038, so no security
  impact).
reproduction: >
  Against the booted container (S5BootedEdgeIT, local profile), send GET /console with no
  Authorization. The response is 404 with body {"message":"Resource not found"}. The test asserts
  this observed 404 and names DEF-0103 in its @DisplayName: "CON-001-a: the console cannot serve the
  contract from a booted service (DEF-0103)". Verified separately: the packaged jar contains no
  openapi.yaml. The contract lives at contracts/billing-edge/openapi.yaml (repo root relative),
  unreachable from a deployed process.
evidence:
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#consoleCannotResolveTheContractFromABootedService"
  - "evidence:run-s5-billing-edge-8f2eec88"
  - "adr:ADR-0038"
  - "evidence:run-s5-billing-edge-r3-e7876edf"
  - "commit:e7876edf4194a32042d19d11a5abdce4447aad3e"
traces_to:
  - "km:node/N-0031"
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#consoleCannotResolveTheContractFromABootedService"
  - "evidence:run-s5-billing-edge-8f2eec88"
status: resolved
blocks_gate: false
triage:
  round: 2
  disposition: >
    Implementation/packaging defect. The console capability (CAP-014, CON-001-a) is broken in every
    non-production environment because the contract is not resolvable from a booted/deployed
    process. The fix is packaging + resolution: bundle the frozen contract into the artifact (e.g.
    src/main/resources/contracts/billing-edge/openapi.yaml) and resolve
    billing.console.contract-location from the classpath (classpath:contracts/billing-edge/openapi.yaml)
    so the console serves it regardless of the process working directory; or mount the contract
    file in the deployment and point the property at the mount. The console is disabled in
    production (ADR-0038), so there is no security or PII exposure — this is a developer-experience
    and contract-visibility gap in non-prod. Autonomously fixable by a java-engineer re-dispatch.
    Round 2 of the bounded loop.

    **Resolution (S5 round-3):** Fix verified green in S5 round-3 (run-s5-billing-edge-r3-e7876edf,
    commit e7876edf4194a32042d19d11a5abdce4447aad3e). The frozen contract is packaged into the artifact at
    src/main/resources/contracts/billing-edge/openapi.yaml (byte-identical to contracts/billing-edge/openapi.yaml)
    and billing.console.contract-location defaults to classpath:contracts/billing-edge/openapi.yaml
    (BillingEdgeProperties.Console.contractLocation), so ConsoleController resolves it from a booted
    process regardless of the working directory. ADR-0038 preserved: the console stays disabled in
    production (application-prod.yaml: billing.console.enabled=false, verified by
    S5ProdProfileIT#theConsoleIsNotServedInProduction). Green test:
    S5BootedEdgeIT#consoleServesTheContractFromABootedService (failsafe, CON-001-a — /console returns
    200 with the published contract body served as browsable documentation). Status advanced
    open→resolved. Not closed: closure is the S7/human gate's call.
  action: >
    Re-dispatch to java-service-engineer (billing-edge): package contracts/billing-edge/openapi.yaml
    into the artifact (src/main/resources/contracts/) and change the console's contract-location
    resolution to a classpath resource (or wire billing.console.contract-location to a deployment
    mount). The S5 test S5BootedEdgeIT#consoleCannotResolveTheContractFromABootedService must flip
    from asserting 404 to asserting 200 + the published contract body. Preserve ADR-0038: the
    console stays disabled in production (CON-001-f already verified under S5ProdProfileIT).
  decided_by: migration-architect
---

# DEF-0103: the console cannot serve the contract from a booted service

Severity:  medium
Found in:  S5 verify of WP-001, billing-edge
Service:   billing-edge
Criterion: CON-001-a
Layer:     console / packaging

## What is wrong

The `@DisplayName` states it:

> "CON-001-a: the console cannot serve the contract from a booted service (DEF-0103)"

`GET /console` returns 404 `{"message":"Resource not found"}`. The test comment records the cause:
`billing.console.contract-location` defaults to `file:contracts/billing-edge/openapi.yaml` — a
path relative to the process working directory — and the contract is **not packaged into the
artifact** (verified: the jar contains no `openapi.yaml`). `ConsoleController` therefore falls to
`notFound()` in every environment where the process is not started from the repository root.

## Severity rationale

**Medium.** The console is disabled in production (ADR-0038, verified by S5ProdProfileIT), so there
is no security or PII exposure — a 404 on a disabled-in-prod documentation path is not a data risk.
The capability CON-001-a (browsable contract docs) is, however, broken in every non-production
environment, which is a developer-experience and contract-visibility regression. Not a gate blocker
on severity.

## Authoritative basis

CON-001-a (criterion): "the API's own published contract is returned as browsable documentation."
ADR-0038 governs console enablement (configuration, disabled in production) and is preserved — the
fix must not enable the console in production.

## Disposition

Class: **implementation** (packaging + resource resolution). Re-dispatch to the billing-edge
java-service-engineer. Round 2 of the bounded loop. Status remains **open**.

## Trace chain

`km:node/N-0031` (console) → `contracts/billing-edge/openapi.yaml` (not packaged) →
`adapter.in.web.ConsoleController` (file: path unresolvable from a deployed process) →
`test:S5BootedEdgeIT#consoleCannotResolveTheContractFromABootedService` →
`evidence:run-s5-billing-edge-8f2eec88`