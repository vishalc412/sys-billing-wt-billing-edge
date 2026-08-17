# ADR-0030: PRESERVE — the BCMS branch still emits an array `billingStatus`, and the case is measured

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0030-defect-bcms-array-billingstatus.md`
Disposition: **preserve (bug-compatible) + instrument; correct in v2 by the "first term" rule**

## Context

N-0078: the BCMS branch does not defend against an array `policyTerm`, so for a BCMS account with
more than one term `billingStatus` becomes an **array**, violating the published type. The DuckCreek
branch defends by taking the last term; the two mappers are "95% identical… only the `billingStatus`
line differs". The DuckCreek defence has its own hole: `sizeOf(null) - 1` yields index `-1`,
undefended. **The BCMS branch has zero test coverage** (R-020) — the case that matters is untested
and unbaselined.

## Decision

Preserve. The BCMS projection emits whatever `policyTerm.status` yields, including an array, and the
OpenAPI types `billingStatus` as `oneOf: [string, array]` with `x-legacy-defect: ADR-0030`. The two
mappers stay separate (ADR-0003), because unifying them while this defect is preserved would itself
change behaviour.

A counter `mule.parity.bcms_multi_term` fires whenever a BCMS account yields more than one policy
term, so the affected population is measured.

**A baseline for the multi-term BCMS case must be captured** (ADR-0021) before S5 signs off this
endpoint. Without it the parity suite passes over the only case that matters — which is what happens
today.

For v2 the correction is the **first term's status** (option C), not the DuckCreek "most recent"
rule: the recorded BCMS rule is "taken as supplied, without selecting the most recent" (N-0078), and
picking the first makes the minimum claim consistent with that, where "most recent" asserts a
DuckCreek rationale onto a system nothing in the knowledge map describes.

## Rejected alternatives

**B — apply the DuckCreek "most recent term" rule to BCMS now.** Rejected twice over: it asserts a
term-ordering semantic for BCMS that no artifact supports, and it changes values for an unmeasured,
untested population. It also happens to be exactly what the pre-existing target-repo code did
silently (ADR-0043), which is why that code is not being adopted.

**C — emit the first term's status now.** Rejected for cutover only on blast radius: `[a, b]`
becoming `a` is a value change for the same unmeasured population, with no baseline to check it
against. It is the v2 answer once the counter and a baseline exist.

**Unifying the two 95%-identical mappers.** Rejected while this defect is preserved — the merge is
the behaviour change.

## Consequences

+ Parity, including the type instability.
+ The population is measured from day one, which is what makes the v2 choice a determination rather
  than a preference.
− A field whose JSON type depends on the data is published, permanently, and cannot be typed
  properly in the contract.
− Preserving it requires the target mapper to *construct* the array case deliberately. Every
  implementer will read that as a bug. This preservation has an ongoing maintenance cost that most
  preservations do not.
− Two near-identical mappers ship, and a future maintainer will merge them unless the note is loud.

## Verification

- Unit: multi-term BCMS fixture yields an array `billingStatus`; single-term yields a string
  (ACC-006). This fixture must be created — none exists today.
- Metric: `mule.parity.bcms_multi_term` asserted present.
- Parity: baseline required per ADR-0021 before sign-off.

## Traces to

`km:node/N-0075, N-0077, N-0078` · `spec:capability/CAP-005` · `risk:R-020` ·
`adr:ADR-0003, ADR-0005, ADR-0021, ADR-0028, ADR-0043`
