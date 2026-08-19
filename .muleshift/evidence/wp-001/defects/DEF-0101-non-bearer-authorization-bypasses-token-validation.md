---
id: DEF-0101
title: "A non-bearer or garbage Authorization header reaches the implementation unauthenticated and returns 200"
service: billing-edge
severity: high
class: implementation
raised_at_stage: S5
summary: >
  An authentication bypass. SecurityConfiguration authorizes anyRequest().permitAll() and the OAuth2
  resource server only validates a token when the Authorization scheme is Bearer; InboundAdmissionRule
  only checks that the header is non-blank. A request with Authorization: Basic ... or Authorization:
  not-even-a-scheme therefore satisfies admission AND skips token validation, reaches the
  implementation, and answers 200. SEC-001-a is mapped to a test that passes (invalid-bearer -> 401)
  but a second shape of the same criterion (non-bearer / garbage scheme) is unguarded. The legacy had
  no inbound auth, so this is not a parity regression; it is the ADR-0013 enforcement gap.
reproduction: >
  Against the booted container (S5BootedEdgeIT, local profile), send GET /info with
  `Authorization: Basic YWRtaW46YWRtaW4=` (or `Authorization: not-even-a-scheme`). The response is
  200 with a body containing "buildNumber". The SEC-001-a test asserts this observed 200 and names
  DEF-0101 in its @DisplayName: "a NON-bearer Authorization header reaches the implementation
  unauthenticated". The invalid-bearer path (Authorization: Bearer <invalid>) correctly returns 401;
  only the non-bearer / no-scheme shape bypasses.
evidence:
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#nonBearerAuthorizationHeaderIsRejectedWith401"
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#garbageAuthorizationValueIsRejectedWith401"
  - "evidence:run-s5-billing-edge-r2-e7876edf"
  - "commit:e7876edf4194a32042d19d11a5abdce4447aad3e"
  - "adr:ADR-0013"
traces_to:
  - "km:node/N-0004"
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#nonBearerAuthorizationHeaderIsRejectedWith401"
  - "evidence:run-s5-billing-edge-r2-e7876edf"
status: resolved
blocks_gate: false
triage:
  round: 2
  disposition: >
    Implementation defect — an authentication bypass. A header that is present and non-blank but not
    a Bearer token passes admission (InboundAdmissionRule only checks non-blank) and the OAuth2
    resource server only engages when the scheme is Bearer, so a non-bearer or garbage Authorization
    value reaches the implementation unauthenticated and returns 200. This is the gap ADR-0013 moved
    into the service to close; the bearer-filter gate must reject any Authorization value that is not
    a Bearer token (or admit only Bearer and require validation) before the implementation runs.
    Autonomously fixable by a java-engineer re-dispatch. Round 2 of the bounded loop.

    **Resolution (S5 round-2):** Fix verified green in S5 round-2 (run-s5-billing-edge-r2-e7876edf, commit e7876edf4194a32042d19d11a5abdce4447aad3e) by S5BootedEdgeIT#nonBearerAuthorizationHeaderIsRejectedWith401 + #garbageAuthorizationValueIsRejectedWith401 (failsafe, 401 on the wire; suite 32 tests, 0 failures). The round-2 fix renamed both tests from the 200-bypass assertion to the 401-rejection assertion. Status advanced open→resolved by migration-architect S6 reconciliation. Not closed: closure is the S7/human gate's call.
  action: >
    Re-dispatch to java-service-engineer (billing-edge): in the inbound security/admission path,
    reject any Authorization header whose scheme is not Bearer with 401 (or treat a non-Bearer
    scheme as a missing bearer token -> 401), so a non-bearer or garbage value can never reach the
    implementation. Correct behaviour per ADR-0013: no operation is reachable without a validated
    bearer token. The two S5 tests
    S5BootedEdgeIT#nonBearerAuthorizationHeaderBypassesTokenValidationEntirely and
    #garbageAuthorizationValueAlsoReachesTheImplementation must flip from asserting 200 to
    asserting 401 (and that no implementation logic runs).
  decided_by: migration-architect
---

# DEF-0101: a non-bearer Authorization header bypasses token validation entirely

Severity:  high
Found in:  S5 verify of WP-001, billing-edge
Service:   billing-edge
Criterion: SEC-001-a (second shape)
Layer:     inbound security

## What is wrong

The `@DisplayName` on the failing test states it directly:

> "SEC-001-a: a NON-bearer Authorization header reaches the implementation unauthenticated (DEF-0101)"

The test sends `Authorization: Basic YWRtaW46YWRtaW4=` to `/info` and asserts the observed **200**,
with an AssertJ description that quotes the criterion:

> "SEC-001-a asserts that a request without a bearer token is rejected and no implementation logic
> runs. It reached the implementation and answered 200."

A second test sends `Authorization: not-even-a-scheme` and also observes 200.

The mechanism is documented in `SecurityConfiguration`: `anyRequest().permitAll()` plus an OAuth2
resource server that validates only when the scheme is Bearer, and in `InboundAdmissionRule`: the
admission check only verifies the header is non-blank. So an arbitrary Authorization value satisfies
admission **and** skips validation.

## Why this is HIGH

This is an authentication bypass on a customer-billing API. A caller who can reach the pod can
skip token validation by sending any non-bearer Authorization header and reach the implementation.
The mapped SEC-001-a test (`SecurityRequiresTokenTest#noOperationIsReachableWithoutAToken`) passes
because it exercises the invalid-**bearer** shape (401); the non-bearer / no-scheme shape is a
second shape of the same criterion that is unguarded. The legacy had no inbound auth (it relied on
an API Manager policy never exported, R-003), so this is not a parity regression — it is the
ADR-0013 enforcement obligation left half-done.

## Authoritative basis

ADR-0013 (inbound authentication placement): the service validates the bearer token itself; no
operation is reachable without a validated token. SEC-001-a is the criterion. The accepted ADR
requires the non-bearer shape to be rejected; the impl lets it through.

## Disposition

Class: **implementation**. Re-dispatch to the billing-edge java-service-engineer. Round 2 of the
bounded loop. **Status: resolved** — fix verified green in S5 round-2 (run-s5-billing-edge-r2-e7876edf);
closure is the S7/human gate's call, not S6's.

## Trace chain

`km:node/N-0004` (the API Manager token binding relocated into the service per ADR-0013) →
`config.SecurityConfiguration` + `domain.api.InboundAdmissionRule` →
`test:S5BootedEdgeIT#nonBearerAuthorizationHeaderBypassesTokenValidationEntirely` →
`evidence:run-s5-billing-edge-8f2eec88`

## Related

DEF-0102 (the same `/info` endpoint, no-Authorization case) is a contract-defect question about the
**absent** header (400 vs the contract's declared 401). DEF-0101 is the **present-but-non-bearer**
case and is unambiguously an implementation/auth gap.