# ADR-0036: PRESERVE — both impersonation definitions ship, each implemented once and tested

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0036-defect-two-impersonation-definitions.md`
Disposition: **preserve (bug-compatible); reconciliation gated on a named compliance owner**

## Context

N-0052 and N-0036 record the same defect from both sides. `isImpersonated` in
`reqresImpersonateFun.dwl` accepts **either** the nested `act` object or the flat `actSub` claim,
because "Ping represents ACT differently based on source (myWF/AWP/etc)" — a documented, deliberate
rule. The per-endpoint logger uses only `!isEmpty(act)`, which is false for flat-`actSub` tokens.
**For such tokens one log stream says the call was impersonated and the other says it was not.**

Three subtleties any reimplementation must preserve: the "no actor" sentinel is the four-character
*string* `"null"`, not a JSON null (and a nested `act.sub` carrying that string **is** reported as
impersonated — asymmetric); a partially-populated `act` missing `sub` or `email` yields `EMPTY`
rather than falling back to the flat claims; and where neither representation supplies a value, the
literal `EMPTY` is recorded. None of this has any test coverage.

Impersonation is an accountability record — it attributes an action taken on a customer's behalf to
both the customer and the internal user.

## Decision

Preserve both definitions and their disagreement. Each rule is implemented **once** — the complete
rule as `ImpersonationRule.complete()`, the incomplete one as `ImpersonationRule.actObjectOnly()` —
and both are fully unit-tested including the `"null"`-string sentinel, the partially-populated `act`
case and the `EMPTY` fallbacks. The endpoint log stream uses the incomplete rule; the request/response
stream uses the complete one, exactly as today.

Reconciliation onto the complete rule is the stated correction and is **gated on R-021 producing a
named compliance owner**, because it changes a compliance-relevant count in the direction of "more
impersonation than we thought" — the kind of metric shift that triggers an investigation into the
wrong thing if it arrives unannounced.

## Rejected alternatives

**B — reconcile onto `isImpersonated` now.** This is the analyst's recommendation and it is
substantively right: the system's record of who acted on whose behalf is wrong for an entire class of
token, and the correct rule is already in the codebase with a comment explaining why. It is rejected
for cutover for one reason only — it changes an audit count that a compliance control may already
use, and **there is nobody identified to tell** (R-021). Shipping an unannounced change to an
accountability metric is worse than shipping a known inconsistency for one more release. The moment
R-021 names an owner, this becomes a one-line change plus a communication.

**C — reconcile onto the incomplete rule.** Rejected: it standardises on the rule the source itself
documents as insufficient.

**Preserving by copying the incomplete rule five times, as the legacy does.** Rejected: five verbatim
copies of a rule already known to be wrong is how this defect arose.

## Consequences

+ Both log streams keep their current content; any compliance control built on either keeps working.
+ Both rules are tested for the first time, including the sentinel branches that have never been
  exercised.
− **Two definitions of an accountability concept ship deliberately.** That is a poor position to
  defend to an auditor, and it is now a written decision rather than an inherited accident.
− Implementers will be tempted to "fix" the incomplete rule. It carries a `@MigratedFrom` note and a
  test that asserts the incompleteness, which will itself look like a mistake.
− If R-021 never produces an owner, the reconciliation never happens. Stated plainly because it is
  the likely outcome.

## Verification

- Unit: both rules over a token matrix — nested `act`, flat `actSub`, `"null"` string in each
  position, partially-populated `act`, neither present (AUD-003).
- Integration: for a flat-`actSub` token the two streams disagree, asserted deliberately.

## Traces to

`km:node/N-0036, N-0050, N-0051, N-0052` · `spec:capability/CAP-003` · `risk:R-020, R-021` ·
`adr:ADR-0017, ADR-0021`
