# ADR-0011: Account-not-found is detected exactly as today, once, and instrumented

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0011-backend-fault-detection.md`

## Context

Four sites decide whether a backend failure means "this account does not exist" using the same
expression (km §6.6; N-0057, N-0084, N-0090, reached again from N-0097):

`errorCode == "204"` **and** `faultstring contains("Account not found")`

Four recorded properties: the match is an exact-case substring; it assumes the fault body is XML (an
HTML proxy page or an empty body evaluates to null and falls through); `errorCode` is compared as
the *string* `"204"`; and it is the only fault shape any flow recognises. There is no WSDL and no
fault taxonomy for either backend (U8, R-017), so the set of faults this test could meet cannot be
enumerated.

The four sites disagree about what to *do* once it matches — that is ADR-0027/0031/0032, not this
ADR.

## Decision

Implement the detection **once**, in `platform-errors` as `BillingFaultClassifier`, reproducing the
legacy expression exactly: exact-case substring on the faultstring, string comparison on
`errorCode`, XML-shaped body assumed, and any parse failure classified as "not a not-found".

Two counters ship with it:

- `mule.parity.fault_code_204_no_match` — a fault carrying `errorCode 204` whose faultstring does
  **not** match.
- `mule.parity.fault_body_unparseable` — a fault body that is not parseable as XML.

Those two counters convert an unknowable into a measurable, and they are the only evidence that
could ever justify widening the condition.

## Rejected alternatives

**B — match on `errorCode == "204"` only.** Rejected: it widens the condition, and with no fault
taxonomy (R-017) we cannot know what other faults carry that code with a different meaning. It is
the right answer the day a taxonomy exists.

**C — case-insensitive, whitespace-normalised match.** Rejected: also a widening, and a silent one.
A fault the legacy routes to the generic 500 path would start producing 204/200. That is a behaviour
change with no consumer check (R-019).

**Leaving four copies as the legacy has.** Rejected: two of the four have already drifted in their
*outcome*, which is precisely how the three-different-not-found-contracts problem arose. One
implementation, three call sites, three explicitly different outcomes chosen by the caller.

## Consequences

+ Exact parity today, including the failure-to-match behaviour on a reworded fault: both systems
  would break identically.
+ The fragility becomes measurable within weeks of go-live.
− A known fragility ships. If the backend team rewords the fault during the migration window, the
  new system breaks and the migration is blamed for a pre-existing condition.
− Two counters may never fire, in which case we have paid a small implementation cost for evidence of
  absence. That is an acceptable price.

## Verification

- Unit: `BillingFaultClassifier` over the recorded fault fixture; `"Account Not Found"` (different
  case) classifies as **not** not-found; a non-XML body classifies as not not-found and increments
  the counter; `errorCode` `204` as a number does not match.
- Integration: each of the three call sites produces its own distinct outcome from one classifier
  result (ACC-007, TXN-003, ESC-003, EXT-005).

## Traces to

`km:node/N-0017, N-0057, N-0084, N-0090, N-0095, N-0097` ·
`spec:capability/CAP-005, CAP-006, CAP-007, CAP-009, CAP-011` · `risk:R-017, R-019`
