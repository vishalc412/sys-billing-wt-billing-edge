# ADR-0042: PRESERVE — all three error body shapes ship, and the contract is rewritten to match them

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0042-defect-raml-commonerror-vs-emitted-shapes.md`
Disposition: **preserve (bug-compatible) in v1; `commonError` deferred to v2**
Assumption-based: **partly — the third shape is unknown pending R-013.**

## Context

N-0001 and N-0069: the RAML declares `commonError`
(`faultActor`/`faultCode`/`faultMessage`/`faultDetail`/`faultTime`/`innerFault`) for 4xx/5xx
including the 597/598/599 timeout codes, but the application emits either a bare
`{"message": "…"}` from the APIkit handlers, or `error_Flow`'s
`faultActor`/`errorDesc`/`errorType`/`errorCause`/`correlationId`, or whatever the opaque
`sapi-common-errorhandler` produces (U1, R-013). Only `faultActor` is common to the published type
and any implemented shape, and even `errorType` is a nested object rather than a flat code. The
597/598/599 codes are produced by nothing the map can see.

## Decision

Preserve all implemented shapes exactly, and regenerate the contract from them (ADR-0012 applied to
the error surface):

- APIkit-equivalent contract violations keep the single-field `{"message": "…"}` body with the exact
  legacy strings ("Bad request", "Resource not found", "Method not allowed", "Not acceptable",
  "Unsupported media type", "Not Implemented").
- Absorbed backend/transformation failures keep the `error_Flow` shape, including `errorCause`
  unredacted (ADR-0015 deliberately does not change the response) and `errorType` as a nested object.
- The unhandled-failure shape is implemented per ADR-0016's assumed contract and documented in the
  OpenAPI with `x-unknown: R-013` until the module owner answers.
- **597/598/599 are explicitly investigated, not quietly dropped.** If the shared handler emits them
  for timeouts, they are live behaviour a consumer may branch on — and an unbounded, unpaged worklist
  query (R-010) makes a timeout code entirely plausible. Until R-013 answers, the target does not
  emit them, and that is recorded as a possible divergence rather than as a decision that they do not
  exist.

RFC 9457 problem details from `platform-errors` are used **only** for responses the legacy did not
produce at all — principally the token-rejection response of ADR-0013. They do not replace any legacy
shape.

## Rejected alternatives

**B — emit `commonError` everywhere, as published.** Rejected: since the contract has been wrong
since at least 2019, every adapted consumer has adapted to the *real* shapes, so "fixing" the
contract breaks all of them to satisfy a document. It would also require inventing a lossy mapping
from `errorType` (an object) and `errorCause` (a raw exception cause) onto `faultCode`/`faultDetail`
— and inventing it for a field ADR-0015 would like to remove.

**D — converge the two locally-produced shapes onto one without adopting `commonError`.** Rejected:
half the risk of B for less than half the benefit, still with no consumer inventory (R-019).

## Consequences

+ Zero consumer impact; every adapted consumer keeps working.
+ The published contract stops lying about the error surface, which is a real improvement with no
  wire change.
− Three different error shapes are published as the official contract of one API. Honest, and ugly.
− One of the three cannot be documented at all until R-013 returns; the contract ships with an
  `x-unknown` marker on the least-understood path in the system.
− Consumers coded to the RAML's `commonError` have been failing quietly for years and will keep
  failing, now with an official document confirming it.

## Verification

- Contract tests: each of the six contract-violation classes returns the exact legacy body and status
  (ADM-003).
- Integration: an absorbed backend failure returns the `error_Flow` shape with all five fields
  (ERR-001).
- CI: every `x-unknown` marker resolves to an open risk id.

## Traces to

`km:node/N-0001, N-0016, N-0024 … N-0030, N-0033, N-0068, N-0069, N-0070` ·
`spec:capability/CAP-001, CAP-011` · `risk:R-010, R-013, R-019` ·
`adr:ADR-0012, ADR-0015, ADR-0016, ADR-0033`
