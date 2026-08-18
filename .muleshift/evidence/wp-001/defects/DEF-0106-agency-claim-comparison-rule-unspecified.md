---
id: DEF-0106
title: "AgencyEntitlementRule uses exact-match List#contains — case-differing and whitespace-padded entitled claims are denied (ADR-0037 states no comparison rule)"
service: billing-edge
severity: medium
class: specification
raised_at_stage: S5
summary: >
  AgencyEntitlementRule decides entitlement with List#contains on the caller's agencyCodes claim
  against the path {agencyCode} — an exact byte comparison. A malformed claim (object not list) is
  correctly fail-closed (denied). But an entitled caller whose claim differs only in CASE
  (agencyCodes: ["a0421"] vs path A0421) or is whitespace-padded (" A0421 ") is denied, even though
  they are entitled to that agency. The legacy served these callers (the legacy had no entitlement
  check at all, per ADR-0037 Context), and ADR-0037 — even as amended — states no comparison
  (case-sensitivity, whitespace-normalisation) rule. The impl chose exact-match (fail-closed), which
  false-denies entitled callers. The specification is silent; the architect must state the rule.
reproduction: >
  Build a CallerContext with agencyCodes ["a0421"] (lowercase) and call
  AgencyEntitlementRule#decide("A0421", caller, enforcing=true). The decision is DENIED (403 on the
  wire). Repeat with agencyCodes [" A0421 "] (whitespace-padded) -> DENIED. The S5 tests assert
  these observed 403s and name DEF-0106 in their @DisplayNames: "an entitled caller whose claim
  differs only in CASE is denied (DEF-0106)" and "...whose claim is whitespace-padded is denied
  (DEF-0106)". The malformed-claim (object not list) case is separately fail-closed and correct.
evidence:
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#caseDifferingAgencyCodeIsDenied"
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#whitespacePaddedAgencyCodeIsDenied"
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#malformedAgencyClaimFailsClosed"
  - "evidence:run-s5-billing-edge-8f2eec88"
  - "adr:ADR-0037"
traces_to:
  - "km:node/N-0035"
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#caseDifferingAgencyCodeIsDenied"
  - "evidence:run-s5-billing-edge-8f2eec88"
status: amended — pending human gate
blocks_gate: false
triage:
  round: 2
  disposition: >
    Specification defect. ADR-0037 (even as amended 2026-08-19) states deny-on-empty-claim, exemption
    owner+expiry mechanics, and enforcement-mode, but it does NOT state the agencyCode comparison
    rule — case-sensitivity and whitespace-normalisation. The impl chose exact-match List#contains
    (fail-closed), which false-denies an entitled caller whose claim differs only in case or
    whitespace from the path agencyCode. The malformed-claim case (object not list) is correctly
    fail-closed and is NOT part of this defect. The architect must amend ADR-0037 (or a follow-on
    ADR) to state the comparison rule — recommended: case-insensitive, whitespace-trimmed, then
    exact-match, so an entitled caller is not false-denied for case/padding differences. Then
    re-dispatch to implement the normalisation in AgencyEntitlementRule. This rides the same
    ADR-0037 assumption-based human gate as DEF-0402/DEF-0403 (the comparison rule is part of the
    same highest-consequence decision). Not a gate blocker on severity (medium, fail-closed — the
    risk is broken legitimate callers at cutover, not data exposure). Round 2 of the bounded loop.
  action: >
    Architect: amend ADR-0037 to state the agencyCode comparison rule (case-insensitive,
    whitespace-trimmed, then exact-match — recommended). Then re-dispatch to java-service-engineer
    (billing-edge): normalise both the path {agencyCode} and each claim value (trim + uppercase)
    before List#contains in AgencyEntitlementRule. The S5 tests caseDifferingAgencyCodeIsDenied and
    whitespacePaddedAgencyCodeIsDenied must flip from asserting 403 to asserting the caller is
    admitted (not 403); malformedAgencyClaimFailsClosed must stay 403 (fail-closed on an unreadable
    claim is correct).
  decided_by: migration-architect
---

# DEF-0106: the agencyCode comparison rule is unspecified; exact-match false-denies entitled callers

Severity:  medium
Found in:  S5 verify of WP-001, billing-edge
Service:   billing-edge
Criterion: SEC-002 (claim-shape robustness)
Layer:     domain/security + specification

## What is wrong

The S5 probe `@DisplayName`:

> "SEC-002: an entitled caller whose claim differs only in CASE is denied (DEF-0106)"

`AgencyEntitlementRule` uses `List#contains` — an exact byte comparison — between the caller's
`agencyCodes` claim and the path `{agencyCode}`. An entitled caller whose claim is `["a0421"]`
(path `A0421`) or `[" A0421 "]` (whitespace-padded) is denied. The test's AssertJ description states
the issue:

> "AgencyEntitlementRule uses List#contains, an exact byte comparison. Fail-closed, but it denies a
> caller the legacy served and ADR-0037 does not state a comparison rule."

The malformed-claim case (object, not list) is separately fail-closed and is **not** part of this
defect — "a claim the service cannot read must not become an allow" is correct.

## Why specification, not implementation

The fail-closed direction is safe (no exposure). The false-deny of an **entitled** caller (case/
padding difference) is a cutover-breakage risk, not a security risk. ADR-0037 — even after the
2026-08-19 amendment — states deny-on-empty-claim, exemption owner+expiry semantics, and
enforcement-mode, but it is **silent** on the comparison rule (case-sensitivity,
whitespace-normalisation). The implementer chose exact-match because the ADR gave no rule. That is
a specification gap, so the class is **specification**, not implementation.

## Severity rationale

**Medium.** Fail-closed (no data exposure). The risk is that an entitled internal caller whose
token agency code differs in case or padding is denied at cutover — exactly the breakage
ADR-0037's Consequences section warns about. Not a gate blocker on the S7 HIGH rule.

## Disposition

Class: **specification**. Architect amends ADR-0037 to state the comparison rule, then re-dispatch
to implement the normalisation. Rides the ADR-0037 assumption-based human gate (same decision as
DEF-0402/DEF-0403). Round 2 of the bounded loop. Status remains **open**.

## Trace chain

`km:node/N-0035` (the agencyCodes claim, captured but never used to authorise — the gap ADR-0037
closes) → `domain.security.AgencyEntitlementRule` (List#contains, exact-match) →
`test:S5BootedEdgeIT#caseDifferingAgencyCodeIsDenied` →
`evidence:run-s5-billing-edge-8f2eec88` · `adr:ADR-0037`

## Related

DEF-0108 (the exemption expiry is never consulted) is a separate implementation defect in the same
`AgencyEntitlementRule` neighbourhood — same ADR-0037, different mechanism. DEF-0402/DEF-0403
(R1, agency) are the ADR-0037 under-specifications already amended; DEF-0106 is a third
under-specification on the same decision.

## Amendment (2026-08-19, S6)

The triage's proposed comparison rule ("case-insensitive, trimmed, then exact-match") was a guess.
It was **verified against the Mule source via the knowledge crib** before being written into the
ADR. The crib finding is that **the legacy performed no comparison at all** — the `agencyCodes`
claim was captured into the audit log only and was never used to authorise the request (N-0035).
Crib grounding (sapi-billing archive):

- `sym:src/main/mule/responseLogFlow.xml#setRequestResponse@L23` /
  `stmt:src/main/mule/responseLogFlow.xml@L24` — the only read of `agencyCodes`:
  `agencyCode: (authentication.properties.userProperties.agencyCodes default [])`, assigned into
  the `requestResponseLog` audit variable. Captured, never compared.
- `route:GET /pastDueToday/{agencyCode}@src/main/resources/api/sapi-billing-search.raml#L107` and
  `route:GET /pendingCancelToday/{agencyCode}@src/main/resources/api/sapi-billing-search.raml#L128`
  — the two agency worklist routes; neither declares or references an entitlement check.
- `stmt:src/main/mule/sapi-billing-search.xml@L206`/`@L209` (and `@L239`/`@L242`) — the path
  `{agencyCode}` is bound from `attributes.uriParams.agencyCode` and passed straight to the
  backend (`implementation.xml` L111, L191); never compared to the claim.

There is therefore **no legacy comparison rule to preserve**. The rule is a **new decision** by
the architect, stated normatively in ADR-0037 (not disguised as heritage). The triage's guess is
not "what the legacy did"; it is what the ADR now decides the target must do.

**ADR-0037 amendment performed.** A new subsection
`### DEF-0106 — agencyCodes comparison rule (2026-08-19, S6)` was added under the existing
`## Amendment (2026-08-19, S6)` section of ADR-0037. The normative rule it states:

1. **Normalise both sides** before comparison — trim surrounding whitespace and uppercase both the
   path `{agencyCode}` and every value in the `agencyCodes` claim.
2. **Exact match after normalisation** — the caller is entitled iff the normalised path value is
   contained in the normalised claim list (normalised exact match; not prefix, not raw substring).
3. **Malformed claim is fail-closed** (unchanged) — a claim that is not a list is a deny; DEF-0106
   concerns only the case/padding false-deny of an *entitled* caller.
4. **Deny-on-empty/absent claim is unchanged.**

Rejected: a case-sensitive or whitespace-sensitive rule — it would convert a cosmetic token/path
mismatch into a denial, with no consumer inventory to predict the blast (R-019); the legacy served
every authenticated caller regardless of case/padding (it served them regardless of the claim
entirely), so a stricter rule manufactures cutover breakage the exemption list exists to catch
piecemeal.

**All four ADR-0037 copies updated byte-identically.** New sha256 across the four copies
(mulesoft-project, billing-agency, billing-account, billing-edge):
`dfad0d4211ecb002118d805e2c01291f61615bec0a854e3baa7a99d182db8075`.

**Re-dispatch flagged (not performed in this act — the architect writes contracts and ADRs
only):** `java-service-engineer` (billing-edge, and the authoritative billing-agency enforcer using
the same claim) must normalise both sides (trim + uppercase) before `List#contains` in
`AgencyEntitlementRule`. The S5 tests `caseDifferingAgencyCodeIsDenied` and
`whitespacePaddedAgencyCodeIsDenied` must flip from asserting 403 to asserting the caller is
admitted; `malformedAgencyClaimFailsClosed` must stay 403.

Status: **amended — pending human gate.** DEF-0106 rides the same assumption-based human gate as
DEF-0402/DEF-0403 (R-003 unresolved; the comparison rule is part of the same highest-consequence
decision and is not an agent's to make alone). ADR-0037 status remains **"Accepted (pending human
approval at the S3 gate)"** — this amendment completes the specification the human will review, it
does not approve it. `triage.round` remains 2. **DEF-0106 is NOT closed.**