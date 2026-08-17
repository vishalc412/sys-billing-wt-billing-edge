# ADR-0031: CORRECT — the external account-not-found path no longer returns a fault body

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0031-defect-external-204-keeps-fault-payload.md`
Disposition: **correct — the body is cleared (consequence of ADR-0020)**

## Context

N-0057: the account-not-found branch of `AccountDetailsFlow` sets `httpStatus` 204 but does not clear
the payload, so an unknown account produces **204 with a SOAP fault body** from an endpoint documented
as JSON. The same shape exists on four sibling paths with a plain SOAP body rather than a fault body
(N-0053, N-0058, N-0063, N-0087). Whether the caller actually receives the body is unknown (U7,
R-009).

## Decision

Correct. On this path and on the four siblings, the 204 response carries **no body**, per ADR-0020.

**Consumers checked:** none could be named (R-019). What was checked instead: the body in question is
a SOAP fault fragment on a status code that RFC 9110 forbids from having a body, delivered from an
endpoint whose published content type is JSON. Most HTTP clients discard a 204 body without exposing
it, and the target's HTTP stack will not emit one without being fought. A consumer that both receives
and parses a SOAP fault from a 204 on a JSON endpoint is a consumer that would be broken by any
backend fault-wording change as well.

## Rejected alternatives

**A — preserve the fault body on the 204.** Rejected: preservation is probably not even implementable
without fighting the HTTP stack, and the thing being preserved is a leak — backend diagnostic text
returned to a caller (overlapping ADR-0015's exposure concern). Preserving a leak by force is not a
defensible use of engineering effort.

**C — return 404 instead.** Rejected: a status change breaks every consumer branching on 204, and it
would make this endpoint inconsistent with its siblings — the opposite of what we are trying to
achieve.

## Consequences

+ A JSON endpoint stops returning XML fault fragments, and one exposure path closes.
+ One rule now governs all five 204 paths.
− It is a divergence from legacy behaviour if the legacy listener does deliver the body, and we have
  not yet observed which (R-009). The observation is assigned to PAR-001 and will confirm or record
  the divergence; it will not reverse the decision unless a consumer is shown to read the body, in
  which case it returns to the architect as a contract defect.

## Verification

- Integration: account-not-found on both `/externalPrimaryAccount` routes yields 204 with
  `Content-Length: 0` (EXT-005).
- Parity: R-009 observation recorded against the baseline; a difference is recorded as a sanctioned
  divergence, not a defect.

## Traces to

`km:node/N-0053, N-0057, N-0058, N-0063, N-0087, N-0091` ·
`spec:capability/CAP-005, CAP-009, CAP-010, CAP-011` · `risk:R-009, R-019` ·
`adr:ADR-0015, ADR-0020`
