# ADR-0037: CORRECT — the service enforces agency entitlement on the worklist endpoints

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, **[human approver — this ADR requires a named human owner]**
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0037-defect-no-agency-entitlement-check.md`
Disposition: **correct (deliberate divergence) — the highest-consequence decision in this set**
Assumption-based: **yes — R-003 (the API Manager policy set) is unresolved and could not be obtained.**

## Context

U6, which the knowledge map names *the highest-consequence unknown in the application*. N-0035: the
`agencyCodes` claim is captured into the audit log but **never used to authorise the request**.
`/pastDueToday/{agencyCode}` and `/pendingCancelToday/{agencyCode}` accept **any agency code from any
authenticated caller**. Corroborated independently at N-0039 ec and N-0041 ec.

The data at stake, for an arbitrary agency code: the named insured's **name and postal address**, the
policy number, the overdue amount, and the due or tentative cancellation date, for every account past
due or pending cancellation today. Customer PII plus financial-distress information, on presentation
of any valid token. `subAgenciesIncluded` widens the query further and the backend's default is
unknown (R-012), so the blast radius of an unentitled request is not even bounded.

Whether an API Manager policy enforces the check **cannot be determined**: the policy set was never
exported (R-003), it was raised as a blocking item for S3, and it has not been supplied. The token
demonstrably carries the `agencyCodes` claim and the application deliberately captures it, which
suggests someone intended an entitlement model and never built it.

## Decision

**Enforce in the service.** `billing-edge` rejects a request for `{agencyCode}` with **403** when the
caller's `agencyCodes` claim does not contain that code. Details, all of which are part of the
decision rather than implementation choices:

- Enforcement is controlled by `billing.security.agency-entitlement=enforce|log-only`, and the
  **default is `enforce`** in every environment including production.
- An **absent or empty** `agencyCodes` claim is a **deny**, not an allow. A caller with no agency
  entitlements has no agency worklist.
- A named exemption list (`billing.security.agency-entitlement.exempt-clients`) exists for service
  accounts that legitimately need cross-agency access. It is empty at scaffold time. Every entry
  requires a named owner and an expiry date recorded in configuration; an entry without both fails
  startup validation.
- `log-only` mode exists so that, if the human approver decides enforcement cannot be enabled at
  cutover, the decision is made explicitly and the exposure is measured while it persists. **Choosing
  `log-only` is a decision to carry a known cross-agency customer-PII exposure into the new system,
  and it requires a named human owner recorded in this ADR.** It is not the default and it is not
  reachable by an engineer's choice.
- R-003 remains open: exporting the policy set is still requested. If a policy already enforces this,
  the service check is redundant and harmless.

**Consumers checked:** none could be named (R-019). The consumers most likely to be affected are
internal reporting jobs or operations tools using a service token with no agency codes — exactly the
population the deny-on-empty-claim rule breaks. That risk is real and is mitigated by the exemption
list, by `log-only` as a deliberate escape hatch, and by shipping the enforcement counter
(`billing.security.agency_entitlement_denied`, tagged with client id) so that a broken caller is
identifiable in minutes rather than by ticket.

## Rejected alternatives

**A — preserve: no check in the service, rely on whatever API Manager does.** Rejected. "Whatever API
Manager does today" is not a statement anyone in this migration can make, because the policy set was
never exported. Preserving an unknown is not preserving a behaviour; it is declining to have one. If
no policy enforces the check, this option knowingly re-ships a cross-agency data-exposure path into a
new system as a deliberate act.

**B — verify first (export the policy set), then match it.** Rejected as a *gate*, retained as a
parallel action: it is the only fact-grounded option, and it was raised as blocking for S3 and has not
returned. Blocking the entire migration on an external team's response time is not a decision, it is
a stall — and if the answer eventually is "no policy enforces it", we would arrive at C anyway, later,
with less time to handle the fallout.

**Enforcing in a gateway rather than in the service.** Rejected: a gateway policy is not testable by
this pipeline. The service check lands in criterion SEC-002 and is verified at S5; a gateway policy is
an assertion in someone else's console.

## Consequences

+ The exposure closes at cutover, under every possible answer to R-003.
+ The check is testable, and its behaviour is the same whether or not a gateway policy exists.
+ The claim is already read for the audit record, so implementation cost is trivial.
− **If no policy enforces this today, some caller is relying on the gap** — plausibly an internal
  reporting job — and it will break, possibly on cutover night. The exemption list and the counter are
  mitigations, not guarantees.
− Deny-on-empty-claim is the strict reading. A service account issued without `agencyCodes` loses
  access immediately.
− We are correcting a security defect with **no consumer inventory**, which is the exact pattern the
  ADR rules warn against. The justification is that the alternative is knowingly re-shipping a
  customer-PII exposure, and that is a worse position to defend than a broken internal job.
− This decision is **not an agent's to make alone**. It is recorded here for a human to accept, reject
  or convert to `log-only` with their name attached.

## Verification

- Integration (SEC-002): a token whose `agencyCodes` omits the requested code yields 403 on both
  worklist endpoints; a token containing it succeeds; an empty claim yields 403; an exempted client
  succeeds and emits a counter tagged with the exemption.
- Startup: an exemption entry without an owner or an expiry fails startup.
- Metric: `billing.security.agency_entitlement_denied` present in the observability contract.

## Traces to

`km:node/N-0021, N-0035, N-0039, N-0040, N-0041, N-0042, N-0051, N-0058, N-0061, N-0063, N-0066` ·
`spec:capability/CAP-002, CAP-010` · `risk:R-003, R-004, R-012, R-019` · `adr:ADR-0013`
