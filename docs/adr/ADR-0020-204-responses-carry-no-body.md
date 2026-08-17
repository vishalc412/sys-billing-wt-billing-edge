# ADR-0020: A 204 response carries no body

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0020-204-with-body.md`

## Context

Five code paths set HTTP 204 without replacing the payload, so the body at the point of response is
still raw SOAP or raw fault content: `/primaryAccount` no-account (N-0087, where the map notes the
empty `<ee:message/>` makes this **deliberate**), both `/externalPrimaryAccount` paths (N-0053,
N-0057), escrow via `primaryAccount-exceptionHandling` (N-0084), and both worklists (N-0058,
N-0063). Sibling endpoints handle the same condition by also replacing the payload with `[]`
(N-0091).

**Whether the caller actually receives that body cannot be determined from source** (U7, R-009). RFC
9110 says a 204 must not have a body, and most Java HTTP stacks enforce it — so the target may be
unable to reproduce the behaviour even if it wanted to.

## Decision

**204 responses in the target carry no body**, on all five paths.

The empirical observation is still required and is assigned (PAR-001 / R-009): a single call against a
legacy environment on a known-missing account, per endpoint. Two outcomes:

- Legacy suppresses the body → this ADR is confirmed and the parity baselines assert an empty body.
- Legacy delivers the body → this ADR stands as a **recorded divergence**, on the grounds that
  emitting XML from a JSON-documented endpoint on a status that forbids bodies is not a contract
  worth fighting the HTTP specification to defend. If a consumer is then shown to read that body, it
  becomes a contract defect routed to the architect at S6, not a re-decision by an engineer.

## Rejected alternatives

**A — determine empirically first and match whatever the legacy does, whatever that is.** Rejected as
the *decision* (retained as the observation): it makes the contract for five paths undecidable until
an environment we may not have (R-025) can be exercised, and it commits us in advance to fighting the
HTTP stack if the answer is inconvenient.

**C — change 204 to 200 with an empty JSON body.** Rejected: a status-code change breaks every
consumer branching on 204. That belongs to ADR-0027's v2 bundle, not here.

## Consequences

+ One rule for five paths; the target's HTTP stack is used as intended.
+ A JSON endpoint stops occasionally returning XML.
− If the legacy does deliver those bodies, this is a silent change for any consumer reading a body on
  a 204. Improbable — most clients discard it — but unverified (R-019).
− Two of the five paths carry *fault* content today (ADR-0031), so suppression also removes whatever
  diagnostic text consumers may have been seeing there.

## Verification

- Integration: all five paths return 204 with `Content-Length: 0` and no body.
- Parity: R-009 observation recorded against ACC-001-c, EXT-003, EXT-005, AGY-006, ESC-003; where the
  observation contradicts the decision, the baseline records the divergence explicitly rather than
  the test being weakened.

## Traces to

`km:node/N-0053, N-0057, N-0058, N-0063, N-0084, N-0085, N-0087, N-0091, N-0095` ·
`spec:capability/CAP-005, CAP-007, CAP-009, CAP-010, CAP-011` · `risk:R-009, R-025` ·
`adr:ADR-0031`
