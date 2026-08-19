---
id: DEF-0110
title: "Every ${BILLING_*} placeholder application-prod.yaml reads is unset by the kustomize deployment manifests — a prod deployment cannot start"
service: billing-edge
severity: medium
class: implementation
raised_at_stage: S5
summary: >
  CFG-001-c/d require the prod profile's placeholders to be supplied by the deployment manifests.
  application-prod.yaml reads 9 ${BILLING_*} placeholders (BILLING_BASE_PATH, BILLING_SAML_AUDIENCE,
  BILLING_ESB_HOST, BILLING_WAS_HOST, BILLING_STS_HOST, BILLING_VAULT_HOST,
  BILLING_TRUSTSTORE_LOCATION, BILLING_TRUSTSTORE_PASSWORD, BILLING_JWT_ISSUER_URI). No kustomize/
  manifest sets any of them. kustomize/ instead sets PRIMARY_ACCOUNT_HOST, EXTERNAL_PRIMARY_ACCOUNT_HOST,
  PING_FED_HOST, TRUSTSTORE_PASSWORD and OAUTH_ISSUER_URI — none of which any profile reads. A
  deployment built from kustomize/ cannot start. The service boots in lower profiles (the S5 suite
  runs); only the prod deployment is blocked.
reproduction: >
  Read src/main/resources/application-prod.yaml and collect every ${PLACEHOLDER} the prod profile
  reads. Walk kustomize/ and collect every "name: <VARIABLE>" the manifests set. The set difference
  (profile reads minus manifest sets) is exactly the 9 BILLING_* names above. The S5 test asserts
  this and names DEF-0110 in its @DisplayName: "every ${...} placeholder the prod profile needs is
  unset by the prod deployment manifest".
evidence:
  - "test:com.westfield.api.billing.edge.s5.S5ProvenanceAndExemptionProbeTest#theDeploymentManifestsSupplyThePropertiesTheProfilesRead"
  - "evidence:run-s5-billing-edge-r2-e7876edf"
  - "commit:e7876edf4194a32042d19d11a5abdce4447aad3e"
traces_to:
  - "mule:flow/sapi-billing-search-main"
  - "test:com.westfield.api.billing.edge.s5.S5ProvenanceAndExemptionProbeTest#theDeploymentManifestsSupplyThePropertiesTheProfilesRead"
  - "evidence:run-s5-billing-edge-r2-e7876edf"
status: resolved
blocks_gate: false
triage:
  round: 2
  disposition: >
    Implementation/deployment-wiring defect. The kustomize manifests set the wrong environment
    variable names (legacy names: PRIMARY_ACCOUNT_HOST etc.) and the prod profile reads BILLING_*
    names, so the 9 placeholders the prod profile needs are unset by every manifest and a prod
    deployment built from kustomize/ cannot start. The service boots in lower profiles (S5ProdProfileIT
    boots only because it supplies the values directly, bypassing the env-var names — its own
    comment points at this defect). The fix is to align the two sides: either rename the kustomize
    manifest env vars to the BILLING_* names the profile reads, or rename the profile placeholders
    to the names the manifests set. Medium — blocks prod cutover but the S5 evidence is valid (the
    service boots when the values are supplied) and there is no data/security exposure. Not a gate
    blocker on the S7 HIGH rule. Autonomously fixable by a java-engineer re-dispatch (deployment
    wiring). Round 2 of the bounded loop.

    **Resolution (S5 round-2):** Fix verified green in S5 round-2 (run-s5-billing-edge-r2-e7876edf, commit e7876edf4194a32042d19d11a5abdce4447aad3e) by S5ProvenanceAndExemptionProbeTest#theDeploymentManifestsSupplyThePropertiesTheProfilesRead (surefire — the 9 ${BILLING_*} placeholders application-prod.yaml reads are now all set by a kustomize manifest; the unsupplied set is empty). Status advanced open→resolved by migration-architect S6 reconciliation. Not closed: closure is the S7/human gate's call.
  action: >
    Re-dispatch to java-service-engineer (billing-edge): align the kustomize deployment manifests
    with the placeholders application-prod.yaml reads — set the 9 BILLING_* env vars
    (BILLING_BASE_PATH, BILLING_SAML_AUDIENCE, BILLING_ESB_HOST, BILLING_WAS_HOST, BILLING_STS_HOST,
    BILLING_VAULT_HOST, BILLING_TRUSTSTORE_LOCATION, BILLING_TRUSTSTORE_PASSWORD,
    BILLING_JWT_ISSUER_URI) in kustomize/base (and the prod overlay), and remove the unused
    PRIMARY_ACCOUNT_HOST / EXTERNAL_PRIMARY_ACCOUNT_HOST / PING_FED_HOST / OAUTH_ISSUER_URI names
    that no profile reads. The S5 test
    S5ProvenanceAndExemptionProbeTest#theDeploymentManifestsDoNotSupplyThePropertiesTheProfilesRead
    must flip from asserting the 9 unsupplied names to asserting an empty set (every placeholder is
    supplied by a manifest).
  decided_by: migration-architect
---

# DEF-0110: prod profile placeholders unset by the kustomize deployment manifests

Severity:  medium
Found in:  S5 verify of WP-001, billing-edge
Service:   billing-edge
Criterion: CFG-001-c, CFG-001-d
Layer:     deployment wiring

## What is wrong

The S5 probe `@DisplayName`:

> "DEF-0110: every ${...} placeholder the prod profile needs is unset by the prod deployment manifest"

`application-prod.yaml` reads 9 `${BILLING_*}` placeholders. `kustomize/` sets none of them; it
instead sets `PRIMARY_ACCOUNT_HOST`, `EXTERNAL_PRIMARY_ACCOUNT_HOST`, `PING_FED_HOST`,
`TRUSTSTORE_PASSWORD` and `OAUTH_ISSUER_URI` — none of which any profile reads. The S5 test asserts
the unsupplied set is exactly:

```
BILLING_BASE_PATH, BILLING_SAML_AUDIENCE, BILLING_ESB_HOST, BILLING_WAS_HOST,
BILLING_STS_HOST, BILLING_VAULT_HOST, BILLING_TRUSTSTORE_LOCATION,
BILLING_TRUSTSTORE_PASSWORD, BILLING_JWT_ISSUER_URI
```

A deployment built from `kustomize/` therefore cannot start: the prod profile's required values are
unset. `S5ProdProfileIT` boots only because it supplies the values directly (bypassing the env-var
names); its own Javadoc comment points at this defect.

## Severity rationale

**Medium.** The service boots in lower profiles and the S5 evidence is valid; only the prod
deployment is blocked. There is no data or security exposure — it is a deployment-wiring
mismatch (manifest env-var names do not match profile placeholder names). It blocks prod cutover
but not the S7 gate on severity.

## Authoritative basis

CFG-001-c ("each environment resolves its own values"), CFG-001-d ("an absent deployment value
fails the start by name"). ADR-0018 (configuration parity; structurally broken configuration fails
at startup — which is exactly what happens here, by name, in prod).

## Disposition

Class: **implementation** (deployment wiring). Re-dispatch to the billing-edge java-service-engineer.
Round 2 of the bounded loop. **Status: resolved** — fix verified green in S5 round-2
(run-s5-billing-edge-r2-e7876edf); closure is the S7/human gate's call, not S6's.

## Trace chain

`mule:flow/sapi-billing-search-main` (the main flow whose back-end hosts the prod profile reads) →
`src/main/resources/application-prod.yaml` (9 ${BILLING_*} placeholders) vs `kustomize/` (sets
different names) →
`test:S5ProvenanceAndExemptionProbeTest#theDeploymentManifestsDoNotSupplyThePropertiesTheProfilesRead`
→ `evidence:run-s5-billing-edge-8f2eec88`