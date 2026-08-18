---
id: DEF-0104
title: "The actuator health probes named by kustomize/base/deployment.yaml answer 404 — the pod never becomes ready"
service: billing-edge
severity: medium
class: implementation
raised_at_stage: S5
summary: >
  kustomize/base/deployment.yaml declares readinessProbe and livenessProbe against
  /actuator/health/readiness and /actuator/health/liveness on port 8081. InboundValidationFilter
  rejects every path absent from ApiResourceTable with the routing 404 before the dispatcher sees
  it, so both probes (and /actuator/health) answer 404 in every profile, including production. The
  pod never becomes ready and a kubernetes rollout cannot complete. The main API works; the
  operational readiness surface does not.
reproduction: >
  Against the booted container (S5BootedEdgeIT, local profile), GET /actuator/health/readiness,
  /actuator/health/liveness, and /actuator/health each return 404 with body
  {"message":"Resource not found"}. The same is observed under the production profile
  (S5ProdProfileIT#actuatorProbesAre404InProductionAsWell). The test asserts the observed 404 and
  names DEF-0104 in its @DisplayName: "the actuator health probes named by
  kustomize/base/deployment.yaml answer 404 (DEF-0104)".
evidence:
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#actuatorHealthProbesAreSwallowedByTheAdmissionFilter"
  - "test:com.westfield.api.billing.edge.s5.S5ProdProfileIT#actuatorProbesAre404InProductionAsWell"
  - "evidence:run-s5-billing-edge-8f2eec88"
traces_to:
  - "km:node/N-0001"
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#actuatorHealthProbesAreSwallowedByTheAdmissionFilter"
  - "evidence:run-s5-billing-edge-8f2eec88"
status: open
blocks_gate: false
triage:
  round: 2
  disposition: >
    Implementation/wiring defect. The admission funnel (InboundValidationFilter + InboundAdmissionRule
    + ApiResourceTable) rejects every path not in ApiResourceTable, including the actuator probe paths
    the kustomize deployment manifest uses. The probes 404 in every profile, so the pod never becomes
    ready and a kubernetes rollout cannot complete. The main API works (verified by S5BootedEdgeIT);
    only the operational readiness surface is broken. Fix: exclude the actuator/management paths
    from the admission filter, or mount actuator on a separate management port/context-path not
    subject to InboundValidationFilter. Autonomously fixable by a java-engineer re-dispatch. Round 2
    of the bounded loop.
  action: >
    Re-dispatch to java-service-engineer (billing-edge): route /actuator/** (or the configured
    management paths) outside InboundValidationFilter (bypass the ApiResourceTable admission check
    for the management surface), or configure management.server.port so actuator binds on a
    separate port not fronted by the admission funnel. Keep the actuator probes unauthenticated
    (k8s probe semantics) but ensure they are NOT exposed on the main listener in production. The
    S5 tests S5BootedEdgeIT#actuatorHealthProbesAreSwallowedByTheAdmissionFilter and
    S5ProdProfileIT#actuatorProbesAre404InProductionAsWell must flip from asserting 404 to asserting
    200 (readiness/liveness) per the kustomize probe contract.
  decided_by: migration-architect
---

# DEF-0104: the actuator health probes answer 404 — the pod never becomes ready

Severity:  medium
Found in:  S5 verify of WP-001, billing-edge
Service:   billing-edge
Layer:     operational / deployment wiring

## What is wrong

The `@DisplayName` states it:

> "the actuator health probes named by kustomize/base/deployment.yaml answer 404 (DEF-0104)"

`kustomize/base/deployment.yaml` declares `readinessProbe` and `livenessProbe` against
`/actuator/health/readiness` and `/actuator/health/liveness` on port 8081. `InboundValidationFilter`
rejects every path absent from `ApiResourceTable` with the routing 404 before the dispatcher ever
sees it, so both probes — and `/actuator/health` — answer 404 in every profile. The same holds under
the production profile (`S5ProdProfileIT#actuatorProbesAre404InProductionAsWell`).

## Severity rationale

**Medium.** The main API works (S5BootedEdgeIT verifies `/info`, `/console`, the admission surface,
and the worklist entitlement filter all serve). Only the kubernetes readiness/liveness surface is
broken. There is no security or PII exposure (the probes are unauthenticated health checks).
Operationally, however, a pod that never becomes ready cannot receive traffic, so a kubernetes
rollout cannot complete — this blocks cutover deployment. Not a gate blocker on the S7 HIGH rule.

## Authoritative basis

The kustomize deployment manifest declares the probe paths; the admission funnel (N-0001,
APIkit validation relocated) must not swallow the management surface. No ADR sanctions 404-on-probe.

## Disposition

Class: **implementation** (admission-filter scope / management-port wiring). Re-dispatch to the
billing-edge java-service-engineer. Round 2 of the bounded loop. Status remains **open**.

## Trace chain

`km:node/N-0001` (APIkit validation, the admission funnel) →
`adapter.in.web.InboundValidationFilter` + `domain.api.ApiResourceTable` (actuator paths absent) →
`kustomize/base/deployment.yaml` (readiness/liveness probe paths) →
`test:S5BootedEdgeIT#actuatorHealthProbesAreSwallowedByTheAdmissionFilter` →
`evidence:run-s5-billing-edge-8f2eec88`