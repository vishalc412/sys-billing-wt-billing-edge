# ADR-0016: `westfield-common-services` is reimplemented to an assumed contract behind an interface, flagged for correction

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0016-opaque-common-services-replacement.md`
Assumption-based: **yes — depends on R-013 and R-005, both unresolved.**

## Context

`westfield-common-services 1.0.12` sits in the request path and is not in the source tree. It
supplies `api-entry/exit-log-subflow` and `JSON_Logger_Config_CL` (N-0015) and
`sapi-common-errorhandler` (N-0016). The map is emphatic about the latter: *"the single largest
behavioural blind spot in the application: it defines what a consumer sees for every unhandled
failure"* (U1, R-013). And about the former: *"any payload or variable mutation they perform is
invisible here and would change downstream behaviour"* (U2, R-005).

One mitigating fact: because every implementation flow uses `on-error-continue`, most backend
failures never reach the shared error handler at all (N-0030 ec). It is reached for failures in
`setRequestResponse`, in the APIkit router, and in the shared logging sub-flows. The blind spot is
large in consequence and narrow in traffic.

## Decision

Split by consequence class.

- **The error handler (N-0016).** Its behaviour is requested from the module owner (R-013, raised
  immediately — it is an email, not an investigation). Until it arrives, `billing-edge` implements
  `UnhandledFailurePresenter` behind an interface, with an **assumed contract** stated explicitly in
  code and in `contracts/billing-edge/openapi.yaml` under `x-unknown: R-013`: the `error_Flow` body
  shape (N-0069) plus the derived status of ADR-0033, and **no** 597/598/599 mapping unless the
  module owner confirms one. The assumption is a named, tracked item, not a silent default.
- **The logging sub-flows (N-0015).** Reimplemented to an *observed* contract captured from a legacy
  instance (task PAR-002, ADR-0021). Their log schema is internal, so an approximation is
  acceptable where an approximation of the error contract is not.
- **The payload-mutation question (R-005) is treated as a live risk, not a formality.** The capture
  in PAR-002 explicitly compares the payload entering and leaving the entry/exit sub-flows. Until
  that comparison exists, every downstream projection in this migration rests on a payload shape we
  have never observed, and the S5 gate must report that as an assumption rather than a pass.

## Rejected alternatives

**A — obtain the module source and port it faithfully, blocking until it arrives.** Rejected as a
*gate*: it blocks CAP-003 and CAP-011, and therefore the whole edge packet, on an external team.
The request is raised in parallel and converts this decision into a verification the moment it
returns.

**B — reimplement everything to an observed contract only.** Rejected for the error handler: it only
captures the paths you think to exercise, and the 597/598/599 timeout mapping is exactly the path
nobody thinks to exercise — especially given the unbounded worklist (R-010) makes timeouts plausible.

**C — define a new error contract and accept divergence on the unhandled-failure path.** Rejected:
that is shipping blind on the one path where we have no data at all about what consumers see today.

## Consequences

+ Nothing blocks. The edge packet proceeds with a named assumption instead of a stall.
+ The assumption is visible in the published contract, not just in an ADR.
− **We will ship a target whose unhandled-failure response may differ from the legacy's**, and we
  will not know until R-013 returns or a baseline is captured. This is the second-largest known
  behavioural risk in the migration after the entitlement decision.
− If R-013 returns after S4 has completed, the correction is a contract amendment and a re-dispatch,
  which the pipeline supports but which costs a round.
− If the entry/exit sub-flows *do* mutate the payload, several projections are built on the wrong
  input shape and the failure will look like a mapping defect at S5 rather than a knowledge defect.
  The S6 triage rule for that pattern is written down in this ADR for that reason.

## Verification

- ERR-003 criteria: unhandled failure produces the assumed shape and status, with the response
  marked in the contract as `x-unknown: R-013`.
- PAR-002: captured baseline for the entry/exit sub-flow payload in and out; a difference is a
  **knowledge defect** routed to S1, not an implementation defect.
- CI: any response documented `x-unknown` must reference an open risk id.

## Traces to

`km:node/N-0006, N-0012, N-0015, N-0016, N-0022, N-0030, N-0031, N-0048` ·
`spec:capability/CAP-003, CAP-011` · `risk:R-005, R-013` · `adr:ADR-0021, ADR-0042`
