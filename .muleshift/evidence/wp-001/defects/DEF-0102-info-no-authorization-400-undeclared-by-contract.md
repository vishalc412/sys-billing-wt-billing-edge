---
id: DEF-0102
title: "/info with no Authorization returns 400, a status the frozen contract does not declare (it declares 200/401/500)"
service: billing-edge
severity: low
class: contract
raised_at_stage: S5
summary: >
  GET /info with no Authorization header returns 400 with the one-field "Bad request" body. The
  frozen contract (contracts/billing-edge/openapi.yaml) declares only 200/401/500 for getBuildInfo,
  and documents 401 as "No token, or a token the service rejected". The impl deliberately returns
  400 for an absent header (the RAML declares the header required, so APIkit answered 400 —
  ADM-003-a, preserved) and 401 only for a present-but-invalid token. The contract under-declares
  the legacy 400; the wire behaviour is legacy-faithful. The architect must amend the contract to
  declare 400 for the absent-required-header contract violation. NOT amended in this triage pass.
reproduction: >
  Against the booted container (S5BootedEdgeIT, local profile), send GET /info with no Authorization
  header. The response is 400 with body {"message":"Bad request"}. The contract's getBuildInfo
  responses block lists '200', '401' ($ref Unauthorised, described as "No token, or a token the
  service rejected"), and '500'. 400 is undeclared. The S5 test asserts the observed 400 and names
  DEF-0102 in its @DisplayName: "SEC-001-b: /info with no Authorization header is rejected — but
  with 400, which the frozen contract does not declare (DEF-0102)".
evidence:
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#infoWithoutAuthorizationIsRejectedWith400NotThe401TheContractDeclares"
  - "evidence:run-s5-billing-edge-8f2eec88"
  - "adr:ADR-0013"
traces_to:
  - "km:node/N-0043"
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#infoWithoutAuthorizationIsRejectedWith400NotThe401TheContractDeclares"
  - "evidence:run-s5-billing-edge-8f2eec88"
status: amended — pending S7 cross-packet verification
blocks_gate: false
triage:
  round: 2
  disposition: >
    Contract defect — the contract is the diverging side. The impl is legacy-faithful: the RAML
    declares the Authorization header required on every operation, so legacy APIkit answered 400
    for an absent header (ADM-003-a; InboundAdmissionRule and SecurityConfiguration both document
    this preserved distinction — absent header = 400 contract violation inside the audit funnel;
    present-but-invalid token = 401 from the resource server). The frozen contract's getBuildInfo
    401 description ("No token, or a token the service rejected") collapses the two conditions
    into 401 and under-declares the 400 the legacy actually produced for a missing header. The
    impl must NOT be changed to return 401 for a missing header — that would retire a legacy 400
    that consumers see today (explicitly warned against in SecurityConfiguration). Recommended
    resolution: amend the frozen contract to declare 400 Bad Request on getBuildInfo (and on
    every operation that requires the header, since the header is required on all of them) for
    the absent-required-header contract violation, alongside the existing 401 for a bad token.
    This is an architect contract-amendment act, NOT performed in this triage pass — flagged for
    a follow-on act. Round 2 of the bounded loop.
  action: >
    Architect follow-on (not in this pass): amend contracts/billing-edge/openapi.yaml getBuildInfo
    (and the shared operation template for every header-required operation) to declare 400 Bad
    Request for the absent-required-header contract violation; re-version the contract and
    re-dispatch affected packets. The impl stands unchanged. No java-engineer re-dispatch from
    this defect.
  decided_by: migration-architect
---

# DEF-0102: /info with no Authorization returns 400, undeclared by the frozen contract

Severity:  low
Found in:  S5 verify of WP-001, billing-edge
Service:   billing-edge
Criterion: SEC-001-b / ADM-003-a (contract declaration)
Layer:     contract

## What is wrong

The `@DisplayName` on the test states it:

> "SEC-001-b: /info with no Authorization header is rejected — but with 400, which the frozen
> contract does not declare (DEF-0102)"

`GET /info` with no Authorization returns 400 `{"message":"Bad request"}`. The frozen contract
`contracts/billing-edge/openapi.yaml` declares only `'200' | '401' | '500'` for `getBuildInfo`, and
the `401` response is described as "No token, or a token the service rejected". 400 is undeclared.

## Which side is authoritative (the architect's determination)

The implementation is legacy-faithful and the contract is the diverging side. Two distinct
conditions produce two distinct legacy responses, and the impl preserves both (documented in
`SecurityConfiguration` and `InboundAdmissionRule`):

- An **absent** Authorization header is a **contract** violation — the RAML declares the header
  required, so APIkit answered 400 with the one-field body (ADM-003-a). This check lives in
  `InboundAdmissionRule`, inside the audit funnel, so the rejection is audited.
- A **present but invalid** token is an **authentication** failure, answered 401 by the resource
  server (R-003 records the gateway-policy shape as an assumption).

`SecurityConfiguration` explicitly warns: requiring authentication at the security layer would
"turn every missing-header request into a 401 and silently retire the 400 that consumers see
today." The contract's 401 description ("No token, or a token the service rejected") conflates the
two conditions and under-declares the 400.

## Recommended resolution

The contract is wrong, not the impl. Amend the frozen contract to declare **400 Bad Request** on
`getBuildInfo` (and on every operation that requires the Authorization header — which is all of
them, per ADM-002-g / ADM-003-a) for the absent-required-header contract violation, alongside the
existing 401 for a present-but-invalid token. The implementation must **not** be changed to return
401 for a missing header.

## Disposition

Class: **contract**. Severity: **low** — the wire behaviour is correct and legacy-faithful; only
the contract declaration is incomplete. This does not block the S7 gate on severity. The contract
amendment is an architect act and is **not performed in this triage pass** — it is flagged for a
follow-on architect contract amendment (re-version + re-dispatch affected packets). Round 2 of the
bounded loop. Status remains **open**.

## Trace chain

`km:node/N-0043` (/info build provenance) → `contracts/billing-edge/openapi.yaml#/paths/~1info/get/responses`
(declares 200/401/500) → `domain.api.InboundAdmissionRule` (absent header -> 400, ADM-003-a) →
`test:S5BootedEdgeIT#infoWithoutAuthorizationIsRejectedWith400NotThe401TheContractDeclares` →
`evidence:run-s5-billing-edge-8f2eec88`

## Related

DEF-0101 is the present-but-non-bearer case (implementation/auth gap). DEF-0102 is the absent-header
case and is a contract-declaration gap only — the impl is correct.

## Amendment (2026-08-19, S6)

The triage's provisional disposition (contract defect; impl legacy-faithful under ADM-003-a; amend
the contract to declare 400) was **verified against the Mule source via the knowledge crib** before
the contract was amended. The crib confirms the legacy answered **400** for an absent Authorization
header on `/info`, not 401:

- `route:GET /info@src/main/resources/api/sapi-billing-search.raml#L20` — the `/info` RAML route
  applies `is: [secured]`.
- `file:src/main/resources/api/exchange_modules/df664b46-69c5-4485-a356-8e70ce82203f/commontraits/1.0.7/secured_bearer.raml`
  — the `secured` trait declares `headers.Authorization.required: true`. A required header that is
  absent is a contract violation APIkit answers with `400 {"message":"Bad request"}` before any
  token-enforcement policy runs (ADM-003-a).
- `sym:src/main/mule/sapi-billing-search.xml#get:\info:sapi-billing-search-config@L251` — the
  `/info` flow (L251–270) has **no flow-level error handler** (no `exc:` node in its span), so the
  required-header violation is answered by APIkit's default 400, not by a 401 from the token policy.
  A present-but-invalid token is the 401 path (resource server, R-003).

Disposition confirmed: **contract defect; impl legacy-faithful; contract amended to declare 400.**
The impl was NOT changed (and must not be — retiring the legacy 400 would be the divergence
`SecurityConfiguration` warns against).

**Contract amendment performed:**

- `contracts/billing-edge/openapi.yaml` re-versioned `1.0.0` → `2.0.0` (a response-code addition is a
  breaking change to the contract surface). A new `components.responses.BadRequest` (400,
  `MessageOnlyError`, example `{"message":"Bad request"}`) is added, described as the
  absent/malformed-required-header contract violation, distinct from `401 Unauthorised`
  (present-but-rejected token). The `Unauthorised` description was refined to remove the
  "No token" conflation so 400 and 401 are crisply distinct.
- `'400': { $ref: '#/components/responses/BadRequest' }` added to `getBuildInfo` responses
  (now `200 | 400 | 401 | 500`).
- The edge contract is duplicated across three repos (billing-edge, billing-agency,
  billing-account); all three copies updated identically. New sha256 across the three copies:
  `e2ad94a4243c5229a9b9547ff4f779400a5006a49d28167754858347b8cdaf15`.
- Migration path: the wire behaviour is unchanged (the impl already answered 400); consumers that
  exhaustively switch on HTTP status must handle `400` on `/info` for an absent/malformed
  Authorization header. The S5 parity test
  `S5BootedEdgeIT#infoWithoutAuthorizationIsRejectedWith400NotThe401TheContractDeclares` already
  asserts the 400 and is now contract-conformant. No java-engineer re-dispatch from this defect.

Status: **amended — pending S7 cross-packet verification.** The S7 gatekeeper confirms the amended
contract now matches the (unchanged) impl across the three copies and that the contract-verify
step passes. `triage.round` remains 2. This amendment is an architect contract act, not a human
approval.