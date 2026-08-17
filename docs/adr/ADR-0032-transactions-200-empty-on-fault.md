# ADR-0032: PRESERVE — `/primaryAccount/transactions` still answers a fault with 200 and `[]`, now instrumented

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0032-defect-transactions-200-empty-on-fault.md`
Disposition: **preserve (bug-compatible) + instrument; split the conflation in v2**

## Context

N-0090, named by the knowledge map as the clearest instance of silent failure in the application: a
backend **fault** produces **200 OK with an empty array**. A consumer cannot distinguish "this
account has no transactions" from "the billing system rejected the request", and the only trace is an
ERROR log line under `SB-PAT-C`. N-0091 adds that monitoring based on HTTP status will never see
these. The flow's own no-account branch does the same thing for a different reason (N-0089), so two
genuinely different situations are conflated into one response.

The *rule* is defensible for the genuinely-empty case — "the caller asked for a list and gets a
list". The defect is that the fault case is folded into it.

## Decision

Preserve the wire behaviour: a fault classified as account-not-found (ADR-0011) yields 200 with `[]`,
logged at ERROR under `SB-PAT-C`, exactly as today. Add the instrumentation of ADR-0010: a counter
tagged with the fault classification, so the path is visible to operations for the first time.

The v2 target is **not** "surface everything as 5xx" but **splitting the conflation**: keep 200 +
`[]` for an account with genuinely no transactions, and surface the fault. That preserves the common,
correct path untouched — most traffic sees no change at all — and targets the actual defect.

## Rejected alternatives

**A — preserve with no instrumentation.** Rejected: it ships an unobservable failure path into a
system that can observe it, and it would let a target-side mapping bug appear as "every account has
no transactions" during cutover. (ADR-0010's carve-out means our own mapper errors return 500
instead, which removes the worst case, but backend faults still need a counter.)

**C — surface the fault as 5xx or 404 now.** Rejected: it breaks any consumer with no error branch,
and the existence of this design is weak evidence that such consumers exist. It would also create a
*fourth* contract for one backend condition unless ADR-0027 is corrected in the same release.

**D — split the conflation now.** Rejected for cutover only because the split cannot be baselined:
there is no golden payload for the fault path (R-020), so we would be changing behaviour with nothing
to compare against. With the counter data and a captured baseline it is the correct v2 change.

## Consequences

+ No consumer change; the invisibility is removed on our side.
+ The counter produces the frequency data that makes the v2 split a measured decision.
− The wire contract remains wrong: a consumer still cannot tell a fault from an empty account.
− Backend outages continue to appear to status-based monitoring as normal traffic. Our counter is
  the only signal, and it needs a dashboard and an owner that do not exist today.

## Verification

- Integration: backend fault classified as not-found yields 200 + `[]` plus one counter increment and
  one ERROR log carrying `SB-PAT-C` (TXN-003).
- Integration: an account with genuinely no transactions yields the identical response with **no**
  counter increment — the two paths must be distinguishable in telemetry even though they are not on
  the wire.

## Traces to

`km:node/N-0079, N-0088, N-0089, N-0090, N-0091, N-0092` · `spec:capability/CAP-006, CAP-011` ·
`risk:R-020` · `adr:ADR-0010, ADR-0011, ADR-0027`
