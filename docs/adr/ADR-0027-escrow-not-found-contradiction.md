# ADR-0027: PRESERVE — the escrow endpoint keeps both of its contradictory "nothing to show" outcomes

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0027-defect-escrow-contradicts-itself-on-empty.md`
Disposition: **preserve (bug-compatible) in v1, with one forced divergence**

## Context

N-0095: the escrow flow reuses `primaryAccount-exceptionHandling`, which returns **204**, while its
own no-result branch returns **200 with `[]`**. Same business outcome, two contracts, and the caller
cannot influence or distinguish which they get. This sits inside a wider three-endpoint disagreement
over the same backend response and the same condition:

| Condition | `/primaryAccount` | `/primaryAccount/transactions` | `/…/escrow/transactions` |
|---|---|---|---|
| No account in response | 204, raw SOAP body | 200 + `[]` | 200 + `[]` |
| Fault "Account not found" | 204, fault body | 200 + `[]` | 204, fault body |

Three contracts for one business condition, and only escrow contradicts itself.

## Decision

Preserve both outcomes on all three endpoints exactly as tabulated, with **one forced divergence**:
per ADR-0020, the 204 responses carry no body. The statuses are unchanged; the bodies on the two 204
paths are empty rather than raw SOAP or fault content.

This decision is taken jointly with ADR-0011 (one detection), ADR-0032 (transactions fault path) and
ADR-0031 (external 204 body), so that the three endpoints continue to differ in exactly the ways they
differ today and in no new way.

Convergence — one status, one shape, non-2xx reserved for genuine failures — is the v2 target and is
part of the single bundled escrow correction (ADR-0023, ADR-0024, ADR-0026).

## Rejected alternatives

**B — converge on 200 + `[]` for both escrow paths.** Rejected: it removes a distinction that a
consumer may be using — today a fault-classified bad account gets 204 while a valid account with no
data gets 200, which is accidental but usable information — and it extends the silent-failure pattern
that ADR-0032 wants to reduce.

**C — converge on 204 for both.** Rejected: it changes the more common path from 200 to 204, breaking
any consumer that unconditionally parses the body.

**D — converge on one 200 object shape across all three endpoints.** Rejected for v1 as the largest
change of the four; retained as the v2 target because it is the only option that puts "no data" and
"the call failed" in the right place, which is the status class.

## Consequences

+ No consumer sees a status change at cutover.
+ The three-way disagreement is now documented in one place instead of being rediscovered per
  endpoint.
− A self-contradicting endpoint ships knowingly, and the target's OpenAPI has to document both
  outcomes for one condition.
− The 204 bodies disappear (ADR-0020). If any consumer reads them, that is a change we chose.

## Verification

- Integration: the four cells of the table above, per endpoint, asserted explicitly (ACC-001-f,
  ESC-002, ESC-003, TXN-002, TXN-003).
- Parity: golden replay where baselines exist.

## Traces to

`km:node/N-0079, N-0084, N-0085, N-0088, N-0090, N-0091, N-0095, N-0096, N-0097` ·
`spec:capability/CAP-005, CAP-006, CAP-007, CAP-011` ·
`adr:ADR-0011, ADR-0020, ADR-0026, ADR-0031, ADR-0032`
