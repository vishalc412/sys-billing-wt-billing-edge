# ADR-0026: PRESERVE — the escrow endpoint keeps its bare `[]` on the no-account path

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0026-defect-escrow-empty-array-vs-object.md`
Disposition: **preserve (bug-compatible) in v1**

## Context

N-0096: the empty response is a bare `[]` while the populated response is an object with
`policyNumber`, `policyVersion` and `escrowTransactions`. Two incompatible shapes on one endpoint,
both with HTTP 200, which no JSON schema can describe and which the RAML (an example only) does not
capture. Note the contrast within the same file: the *populated-but-no-escrow-account* case correctly
returns the object with `escrowTransactions: []`. The endpoint already knows how to express "nothing
to show" as an object; the bare `[]` is used only for the no-account case.

## Decision

Preserve. The no-account path returns a bare `[]` with HTTP 200. The OpenAPI types the 200 response
as `oneOf: [EscrowTransactionsResponse, EmptyArray]` with `x-legacy-defect: ADR-0026`.

Correction is deferred to the v2 frame of ADR-0012 and, when it happens, must happen as a **single
bundled escrow correction** together with ADR-0023, ADR-0026 and ADR-0027. Correcting the empty
shape alone leaves an endpoint that is still internally inconsistent, which is barely an improvement.

## Rejected alternatives

**B — return the object shape with an empty `escrowTransactions` on the no-account path.** Rejected
for v1: a consumer testing `length === 0` on an array would receive an object. Low blast radius, but
unverified (R-019) — and the no-account case has, by definition, no billing data at all to populate
`policyNumber` from, so B also forces a third decision (emit the caller's value, or `null`) that
belongs with ADR-0023 and ADR-0024. This is the right v2 answer as part of the bundle.

**C — return `{"escrowTransactions": []}` with the identifier keys omitted.** Rejected: it invents a
third shape to fix a two-shape problem.

## Consequences

+ Parity; a consumer that special-cases an array response keeps working.
− The endpoint's response type remains undescribable in a single schema, so the target's OpenAPI
  inherits the problem and publishes a `oneOf` where a type belongs.
− A generated client cannot deserialise both shapes into one type. Any such consumer is already
  doing something defensive or already failing — and we now know that and are choosing not to act.

## Verification

- Integration: no-account backend response yields 200 with a bare `[]`; populated response yields the
  object (ESC-002).
- Contract: the `oneOf` is present in `contracts/billing-account/openapi.yaml` with the defect marker.

## Traces to

`km:node/N-0047, N-0095, N-0096, N-0100` · `spec:capability/CAP-007` · `risk:R-019` ·
`adr:ADR-0012, ADR-0023, ADR-0024, ADR-0027`
