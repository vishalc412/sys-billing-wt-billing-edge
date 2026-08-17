# ADR-0019: The mandatory-but-unused `environment` query parameter is preserved exactly

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0019-environment-query-parameter.md`

## Context

The RAML's `environment` trait makes an `environment` query parameter mandatory on five endpoints —
both `/externalPrimaryAccount/*` routes and both worklists (N-0001 ec). **No flow reads it**; the
back end is chosen entirely by `mule.env` (N-0034 ec, N-0038 ec). APIkit rejects a request that omits
it with 400 and the fixed body `{"message": "Bad request"}`, giving no indication of which validation
failed (N-0024). The three `/primaryAccount*` endpoints do not carry the trait, so the API is
internally inconsistent about it (N-0045 ec).

## Decision

Preserve exactly: the parameter stays **required** on the same five endpoints, its value is validated
against the same enumeration, and it continues to have no effect. Omission yields 400 with the same
fixed body. The generated OpenAPI documents it as required with a description stating plainly that it
is ignored, and carries `x-legacy-defect: ADR-0019`.

Criterion ADM-002-d asserts this behaviour explicitly, because "required parameter that nothing
reads" is exactly what an implementer deletes as dead code.

## Rejected alternatives

**B — make it optional, accept and ignore it.** Rejected for cutover: every existing caller sends it
already (they could not be working otherwise), so loosening helps nobody today, while any consumer
whose contract test asserts the 400 breaks. Low probability, non-zero, and entirely avoidable. It is
the right change in a v2 (ADR-0012).

**C — remove it and reject requests carrying it.** Rejected: breaks every existing caller.

**D — honour it and select the back end from it.** Rejected: per-request backend selection is a
significant new capability with an obvious security consequence (a caller choosing `PROD` from a
non-production deployment). Firmly out of scope.

## Consequences

+ Zero risk, zero coordination.
− A brand-new system publishes a required parameter that does nothing, deliberately. Every new
  consumer will ask why, and the answer is "no reason".
− The internal inconsistency (three endpoints without the trait) is preserved too.

## Verification

- Contract: the five endpoints declare `environment` as required; the three `/primaryAccount*` do
  not.
- Integration: omitting it yields 400 with the exact legacy body (ADM-002-d, ADM-003).

## Traces to

`km:node/N-0001, N-0024, N-0034, N-0038, N-0039, N-0041, N-0045` ·
`spec:capability/CAP-001, CAP-009, CAP-010` · `adr:ADR-0012`
