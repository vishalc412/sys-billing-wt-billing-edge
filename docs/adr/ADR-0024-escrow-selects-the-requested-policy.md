# ADR-0024: CORRECT — the escrow endpoint selects the policy term the caller asked for

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0024-defect-escrow-ignores-caller-policy-params.md`
Disposition: **correct (deliberate divergence from legacy behaviour)**

## Context

N-0100 (`escrowTransactions-implementation.xml:52-59`): the caller's `policyNumber` and
`policyVersion` are captured into variables and **never used**. Which policy term's escrow
transactions are returned is decided entirely by the shape of the backend response — the first term
carrying more than a version number, in document order. And because `duplicateKeyAsArray` makes
`policies` an array on a multi-policy account, `policies.policyTerm` returns terms across **all**
policies, so the projection can mix terms from different policies. The RAML declares `policyNumber`
**required**, so the caller is compelled to send a value that is validated, captured and discarded.

The candidate stated that the architect must classify this: correctness defect, or confidentiality
defect. **I classify it as a confidentiality defect.** On a multi-policy account the endpoint can
return one policy's escrow ledger to a caller who asked about another, and escrow data is customer
financial data.

## Decision

Correct at cutover. The escrow projection selects the policy term belonging to the caller's
`policyNumber`, narrowed further by `policyVersion` when supplied. When the requested policy has no
term in the backend response, the endpoint returns the **object shape with an empty
`escrowTransactions` list** — which is exactly what the legacy already does for a policy term with no
escrow account (N-0100 rule), so no new response shape is invented.

Where several terms belong to the requested policy, the legacy tie-break is preserved: the first
term in document order carrying more than a version number.

A counter `mule.parity.escrow_term_mismatch` fires whenever the corrected selection differs from what
the legacy shape-based rule would have chosen, so the affected population is measured from day one.

**Consumers checked, and the honest answer:** there is no consumer inventory (R-019) and no consumer
was checked. What was checked is the shape of the change. For a single-policy account with one
populated term — the case every existing fixture and every MUnit test exercises — the corrected
selection returns the identical response. The change is observable only on accounts where the legacy
was returning escrow data for a policy the caller did not ask about, i.e. exactly the population the
defect harms. A consumer depending on that is depending on receiving another policy's data.

## Rejected alternatives

**A — preserve the shape-based selection.** Rejected: it reproduces a data-disclosure path
deliberately. "The old system did it" is not a defensible answer to a question about customer
financial data being returned to the wrong requester, and it is the one disposition in this register
that I am not willing to defend to a security reviewer.

**C — preserve and instrument for a bounded window, then correct.** Rejected, and this is the closest
call in the register. C is the analyst's recommendation and it is methodologically better: it
measures before changing. It is rejected because the measurement window *is* the exposure window, the
correction is not made cheaper or safer by knowing the rate, and no rate is low enough to justify
shipping the path into a new system as a deliberate act. The instrumentation is kept — the counter
ships with the correction, so the population is still measured, just not exploited while we wait.

## Consequences

+ The endpoint answers the question it was asked. The disclosure path closes at cutover.
+ `policyNumber` is already required and validated, so no contract change is needed to do it.
− **This is a behaviour change shipped without a consumer inventory.** A consumer that has
  unknowingly been consuming another policy's escrow ledger will see different — correct — data and
  may report it as a regression. That must be in the cutover communication.
− The parity suite must record a deliberate divergence on this endpoint. Any parity failure here is
  expected and is a *sanctioned divergence*, not a defect (S6 triage rule).
− The response for "requested policy has no term" is a case the legacy never produced in that form.
  It reuses an existing legacy shape, but it is still a path with no captured baseline (R-020).
− ADR-0023 keeps the misnamed `policyNumber` field, so after this correction the transactions belong
  to the requested policy while the field named `policyNumber` still carries the account number. That
  combination is defensible (the safe order) but it is not tidy, and anyone reading the response
  without both ADRs will be confused.

## Verification

- Unit: multi-policy, multi-term fixtures asserting the selected term belongs to the requested policy;
  `policyVersion` narrowing; requested policy absent yields the object shape with `[]` (ESC-001).
- Metric: `mule.parity.escrow_term_mismatch` present and asserted in the observability contract.
- Parity: this endpoint's parity expectations are re-baselined against the corrected behaviour, with
  the divergence recorded in the golden-payload metadata.

## Traces to

`km:node/N-0047, N-0079, N-0080, N-0098, N-0100` · `spec:capability/CAP-007` ·
`risk:R-019, R-020` · `adr:ADR-0005, ADR-0023, ADR-0026, ADR-0027`
