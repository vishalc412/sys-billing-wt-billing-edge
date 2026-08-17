# ADR-0028: PRESERVE — the account mapper is still chosen by the `6000` prefix, once, and instrumented

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0028-defect-duckcreek-routing-by-account-prefix.md`
Disposition: **preserve (bug-compatible), with two non-observable fixes**

## Context

N-0077: the choice between the DuckCreek and BCMS mapping variants is made on an account-number
prefix (`"6000"`) rather than on the `billingSystemCode` field present in the very response being
mapped. Three aggravations: the literal appears **twice** (line 15 for routing, line 185 as a logged
`isDuckcreek` flag) so one can change without the other; the comparison is made on the **query
parameter**, not the response's `accountNumber`, so a disagreement routes to the wrong branch; and
`vars.billingAccountNumber` has no default, so an absent parameter makes `null startsWith "6000"`
**raise** inside the choice.

Getting the branch wrong is not cosmetic: the branches differ in how `billingStatus` is derived, and
only one defends against an array `policyTerm` (ADR-0030).

## Decision

Preserve the routing rule: the DuckCreek projection is selected when the **request's**
`billingAccountNumber` starts with `6000`, exactly as today.

Two changes that are not observable to any consumer, and are therefore made now:

1. The `6000` literal exists **once**, as a named constant used by both the routing decision and the
   `isDuckcreek` log field.
2. The absent-account-number path is **deterministic**: a missing `billingAccountNumber` is rejected
   by request validation (as APIkit does today) and can never reach the branch. Reaching the mapper
   with a null account number is a programming error that fails fast with a 500, not a coercion error
   carrying a backend status code.

Two counters ship: `mule.parity.billing_system_prefix_mismatch` (the prefix decision disagrees with
`billingSystemCode`) and `mule.parity.billing_system_code_unknown` (a `billingSystemCode` value other
than `DuckCreek` or `BCMS`).

## Rejected alternatives

**B — route on `billingSystemCode` from the backend response.** Rejected for cutover, though it is
obviously right in principle: the entire effect of the correction lands on the accounts where prefix
and code disagree — an unmeasured population — and its safety depends on a field whose value domain
nobody can enumerate, because there is no schema (R-017). Only two values have ever been observed. An
unexpected third value would have no defined branch, and the failure would be a silently wrong
projection rather than an error.

**Correcting the comparison to use the response's `accountNumber`.** Rejected for the same reason: it
changes the branch for exactly the accounts the defect affects, blind.

## Consequences

+ Parity, including for accounts where the two disagree.
+ The duplication hazard and the null-dereference path are gone, with no observable change.
+ Within weeks of go-live the counters produce the population size that makes B a measured decision.
− A magic literal encoding a business fact ships in a new system.
− If DuckCreek migration continues and accounts appear outside the `6000` range, the target is wrong
  from day one — and the knowledge map cannot tell us whether that is already happening. The
  `billing_system_prefix_mismatch` counter is the early warning, and it only helps if someone watches
  it. No owner is named today.

## Verification

- Unit: account `6000077687` takes the DuckCreek projection, `3476501380` takes BCMS; a mismatch
  between prefix and `billingSystemCode` increments the counter and does **not** change the branch
  (ACC-008).
- Unit: the constant is referenced from both sites (single-source assertion).
- Integration: a request without `billingAccountNumber` is rejected by validation with the legacy 400
  body.

## Traces to

`km:node/N-0075, N-0077, N-0078, N-0080, N-0083` · `spec:capability/CAP-005, CAP-008` ·
`risk:R-017` · `adr:ADR-0030`
