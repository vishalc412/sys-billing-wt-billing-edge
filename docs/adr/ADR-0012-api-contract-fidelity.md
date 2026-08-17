# ADR-0012: The contract is regenerated from implemented behaviour; corrections wait for a v2 that has no owner yet

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0012-api-contract-fidelity.md`

## Context

The published RAML and the implemented behaviour disagree in at least eight recorded places: the
`commonError` body shape (ADR-0042), `eft` as a string (ADR-0029), an occasionally-array
`billingStatus` (ADR-0030), an escrow `policyNumber` that is an account number (ADR-0023), escrow
`amountDue` as a string (ADR-0025), an unwrapped external response (N-0056 ec), a mandatory
`environment` parameter nothing reads (ADR-0019), and two endpoints with no declared response type at
all. `protocols: [HTTPS]` is declared while the listener is plain HTTP behind upstream TLS
termination, and `/info` sits behind the `secured` trait.

**Nobody has enumerated the consumers of this API** (R-019). The map evidences that consumers exist
— a customer experience layer, agency-facing systems, an upstream experience API supplying
`x-api-key` — but not who they are. There is also documented evidence the contract has already
broken once without record (R-027, the stale `_0Trans`/`_1Trans` fixtures).

This is the parent decision for the whole 0023–0042 defect family.

## Decision

**Preserve implemented behaviour byte-for-byte at cutover, and publish `contracts/*/openapi.yaml`
generated from the implemented behaviour rather than from the RAML.** Where implementation and RAML
disagree, the OpenAPI documents the implementation, and each such place carries an explicit
`x-legacy-defect: ADR-00NN` extension so that the contract states which of its own statements are
known defects being preserved.

Every defect ADR in the 0023–0042 range decides "preserve" **within this frame**: preserve *in v1*,
not preserve forever.

A corrected **v2** is the stated route for every deferred correction. It is **not committed**, and
this ADR says plainly why: a v2 requires a consumer inventory (R-019), and R-019 has no owner today.
Per the ADR authoring rules, a follow-up without an owner is a decision never to do it. If the human
approver wants v2 to happen, the output of this gate must be a named owner for R-019, not an
intention.

## Rejected alternatives

**B — publish v1 and v2 simultaneously at cutover.** Rejected: two contracts, two parity suites and
double the S5 surface, purchased before anyone knows whether a single consumer would move to v2. It
becomes the right answer the moment R-019 produces a consumer list.

**C — correct the contract in place at cutover.** Rejected: a behaviour change shipped blind against
an unenumerated consumer set. This is exactly what the ADR rule on corrections exists to prevent.

**Publishing the RAML as-is and treating the implementation as the defect.** Rejected: it would
leave the target's own generated contract lying in the same way the RAML lies today, and the
contract is the coordination mechanism the S4 fleet builds against.

## Consequences

+ Zero consumer impact at cutover; the contract stops lying without a single wire byte moving.
+ The S4 agents build against a contract that matches the parity suite, so contract tests and parity
  tests cannot disagree.
+ Each preserved defect is visible *in the contract*, not only in an ADR.
− We publish `eft: "false"`, an account number named `policyNumber`, and an occasionally-array
  `billingStatus` as the official contract. That blesses the defects, and the `x-legacy-defect`
  markers are a partial mitigation at best.
− A consumer that coded to the RAML and has been quietly failing since 2019 keeps failing, and now
  has an official document saying so.
− Contract regeneration cannot be completed for the unhandled-failure path until R-013 is answered
  (ADR-0042). That part of the contract ships marked `x-unknown: R-013`.

## Verification

- Contract tests (`platform-testing` harness) run each module's implementation against its frozen
  `contracts/<module>/openapi.yaml` in CI.
- Every `x-legacy-defect` marker in a contract resolves to an accepted ADR (mechanical check in CI).

## Traces to

`km:node/N-0001, N-0013, N-0029, N-0036, N-0043, N-0046, N-0047, N-0056, N-0069, N-0077, N-0078,
N-0096, N-0100` · `spec:capability/CAP-001, CAP-005 … CAP-011, CAP-013` · `risk:R-019, R-027`
