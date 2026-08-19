# ADR-0037: CORRECT — the service enforces agency entitlement on the worklist endpoints

Status:   Accepted (pending human approval) — app-layer non-enforcement is now evidenced; the open question is solely whether the API Manager gateway (R-003, policy set not supplied) already enforces agency entitlement.
Date:     2026-08-14
Deciders: migration-architect, **[human approver — this ADR requires a named human owner]**
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0037-defect-no-agency-entitlement-check.md`
Disposition: **correct (deliberate divergence) — the highest-consequence decision in this set**
Assumption-based: **partially — app-layer non-enforcement is now evidenced (S1 re-excavation, 2026-08-19); only the R-003 gateway question remains unresolved.**

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

## Evidence (S1 re-excavation, 2026-08-19)

The mule-archaeologist re-excavated the user-supplied source to ground this ADR's highest-consequence
claim. The findings below convert the app-layer portion of the rationale from assumption to evidence.
They do **not** close R-003; the gateway question remains open.

- **`global.xml:20`** — `api-gateway:autodiscovery apiId="16888213"` binds the application to an API
  Manager instance. Inbound policy enforcement (including any agency-entitlement check) is a platform
  policy configured in API Manager, **not in application source**. R-003 is that policy set, and it was
  never supplied. This is why enforcement at the gateway cannot be confirmed or ruled out from the
  application archive alone.
- **`responseLogFlow.xml:24` (knowledge-map node N-0035)** — `agencyCodes` is read from
  `authentication.properties.userProperties.agencyCodes` with `default []` and copied into the
  `requestResponseLog` audit record. It is **never compared** to the `{agencyCode}` path parameter and
  **never gates** a request. The claim is audit-only.
- **`sapi-billing-search.raml`** — every resource applies the `secured` trait (bearer `Authorization`
  header) but declares **no `securedBy:` scheme** for agency entitlement. `/pastDueToday/{agencyCode}`
  and `/pendingCancelToday/{agencyCode}` accept **any agency code from the path** with no check. There
  is no entitlement scheme in the API contract.
- **N-0021 (`authentication.properties`, ADR-0013)** — the application only **reads** claims
  (`act` / `actSub` / `actEmail` / `agencyCodes` / `clientId`); it implements **no authentication and no
  authorization itself**. Auth is gateway-owned (ADR-0013); the app is a claim consumer.

**Verdict.** The source **supports** the claim that agency-entitlement enforcement is **new behaviour
the legacy does not have at the app layer**: the `agencyCodes` claim is captured for audit and never
compared, the RAML declares no entitlement scheme, and the app owns no auth. The source **leaves open**
whether the API Manager gateway already enforces it (R-003 not supplied — cannot be confirmed from
source). Therefore ADR-0037's deny-on-absent/empty-claim rule is an **addition, not a preservation**,
and the in-service enforcement (billing-agency rejects on its own worklist endpoints; billing-edge MAY
enforce as defense-in-depth) is **new behaviour, not a parity regression**. This grounding strengthens
the rationale; it does **not** change the Decision, the exemption-list mechanics, or the DEF-0106
comparison rule (normalise both sides trim + uppercase Locale.ROOT, exact match, malformed claim
fail-closed, deny-on-empty/absent).

## Decision

**Enforce in the service.** `billing-agency` rejects a request for `{agencyCode}` with **403** on its
own worklist endpoints (`/pastDueToday/{agencyCode}`, `/pendingCancelToday/{agencyCode}`) when the
caller's `agencyCodes` claim does not contain that code. `billing-edge` MAY enforce the same check as
defense-in-depth, but it is **not the sole enforcer** — the service that owns these paths is
authoritative. (Corrected: an earlier draft of this body said "billing-edge rejects", which
contradicted the title, the rejected-alternatives, and the frozen contract's 403-on-these-paths
declaration. See the Amendment note.) Details, all of which are part of the decision rather than
implementation choices:

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

## Amendment (2026-08-19, S6)

S6 triage (DEF-0403 and DEF-0402, see `docs/07-TRIAGE-R1.md`) found two under-specifications in this
ADR, both on its highest-consequence assumption-based decision. This amendment corrects them in
place. It does **not** approve the assumption-based decision — R-003 (the API Manager policy set)
remains open and the ADR still requires a named human approver at the S3/S7 gate. **Status remains
"Accepted (pending human approval at the S3 gate)."** This correction removes an internal
inconsistency; it does not approve the assumption-based decision (R-003 remains open and requires a
named human approver).

### DEF-0403 — enforcement point contradiction (corrected)

The Decision body's first clause originally said "`billing-edge` rejects a request for
`{agencyCode}` with 403", which contradicted (a) this ADR's own title ("the service enforces agency
entitlement on the worklist endpoints"), (b) the rejected-alternatives section ("Enforcing in a
gateway rather than in the service. Rejected… The service check lands in criterion SEC-002 and is
verified at S5"), and (c) the frozen contract (`contracts/billing-agency/openapi.yaml`), which
declares `403: EntitlementDenied` on `/pastDueToday/{agencyCode}` and
`/pendingCancelToday/{agencyCode}` — billing-agency's own paths — with the description "Entitlement is
now enforced (ADR-0037)" and security scheme "Validated by the service (ADR-0013)".

The title and the frozen contract are authoritative and they agree: **the service (billing-agency)
enforces.** The body's "billing-edge rejects" wording was the error. The body's first clause is
corrected above to align with the title, the rejected-alternatives, and the contract.

The corrected ruling is: **the service (billing-agency) enforces; `billing-edge` MAY enforce the
same check as defense-in-depth but is not the sole enforcer.** The service that owns
`/pastDueToday/{agencyCode}` and `/pendingCancelToday/{agencyCode}` is authoritative. The
billing-agency implementation (`AgencyEntitlementRule` + `AgencyEntitlementGate`, nine SEC-002
endpoint tests, fail-closed) matches this ruling exactly — **no re-dispatch is required for
billing-agency.**

**Secondary risk (the triage's flagged metric double-count):** the deny counter
`billing.security.agency_entitlement_denied` may be incremented twice for one request if both
billing-edge and billing-agency enforce. If billing-edge's defense-in-depth is enabled, it must
**attribute per-service** (tag the counter with the enforcing layer) so the metric stays countable
and a broken caller is identifiable rather than double-counted. The authoritative counter is the
service's; billing-edge's is auxiliary.

This amendment does not close DEF-0403. DEF-0403 remains **open — pending (a) human approval of the
assumption-based ADR-0037 (R-003 unresolved) and (b) the S7 gatekeeper's cross-packet enforcement
check** (verify, across both WP-001 and WP-003, that the entitlement check is implemented in at least
one place and the cross-agency PII exposure is closed). Neither is an S6 act.

### DEF-0402 — exemption-list mechanics (now normative)

The three exemption-list mechanics the triage flagged as under-specified are stated here
**normatively**. The billing-agency module already implements all three fail-closed, so this makes
existing behaviour normative rather than changing it.

1. **Exemption-list accessor placement.** The exemption list should be deployable-wide like the mode.
   The single source of truth for enforce-vs-log-only is
   `LegacyBehaviourFlags.enforceAgencyEntitlement()` in `platform-spi`; the exemption list should
   have an equivalent accessor there. The current module-local binding via
   `AgencySecurityProperties` is a **safe interim** — the implementation is fail-closed and
   startup-validated, and no behaviour depends on the placement, only on the fail-closed semantics.
   A `platform-spi` accessor is a **follow-on contract re-version**, not re-dispatched now.

2. **Expiry semantics (normative).** Past the expiry date the caller is treated as **unexempted**
   and the deny rule applies. The boundary is **inclusive — the exemption is live *through* its
   expiry date** (an exemption dated 2026-09-30 is active on 2026-09-30 and denied on 2026-10-01).
   This matches the implemented `ExemptClient.isActiveOn` behaviour and is stated here to make it
   normative, not an implementation choice. The alternative reading (the date is decoration and the
   exemption persists) is rejected — it would make the mandatory expiry field meaningless.

3. **`CallerContextPort` placement.** Recommend `platform-spi`. The `CallerContext` record is
   already frozen in `platform-spi`; the port that supplies the current request's instance should
   live with it. Every service that reads the caller will need the same port, and a port declared
   per-service is how the legacy's fault classifier ended up with four copies that drifted.
   Follow-on contract re-version, not re-dispatched now.

Items 1 and 3 are follow-on `platform-spi` contract re-versions; they do not change behaviour and
are not re-dispatched in this round. DEF-0402 remains **open — pending (a) human approval of the
assumption-based ADR-0037 (R-003 unresolved) and (b) the S7 gatekeeper's traceability check**. The
current implementation is safe (fail-closed on all three mechanics) and can stand pending the
amendment's contract re-version and the human gate.

### DEF-0106 — agencyCodes comparison rule (2026-08-19, S6)

DEF-0106 (S5, billing-edge) flagged that `AgencyEntitlementRule` decides entitlement with
`List#contains` — an exact byte comparison between the caller's `agencyCodes` claim and the path
`{agencyCode}` — and that this ADR stated no comparison rule (case-sensitivity, whitespace
normalisation). The triage's "case-insensitive, trimmed, then exact-match" was a guess. This
subsection verifies the rule against the legacy source via the knowledge crib and states it
**normatively**.

**What the legacy actually did (verified via the crib, sapi-billing archive): the legacy performed
no comparison at all.** The `agencyCodes` claim was captured into the request/audit log only and
was never read to authorise the request — N-0035 (this ADR's Context). The crib grounds this at the
source level:

- `sym:src/main/mule/responseLogFlow.xml#setRequestResponse@L23` / `stmt:src/main/mule/responseLogFlow.xml@L24`
  — the only place `agencyCodes` is read: `agencyCode: (authentication.properties.userProperties.agencyCodes default [])`,
  assigned into the `requestResponseLog` audit variable. Captured, not compared.
- `route:GET /pastDueToday/{agencyCode}@src/main/resources/api/sapi-billing-search.raml#L107` and
  `route:GET /pendingCancelToday/{agencyCode}@src/main/resources/api/sapi-billing-search.raml#L128`
  — the two agency worklist routes, both `is: [commonErrors, secured, environment]`, with no
  entitlement check declared or referenced.
- `stmt:src/main/mule/sapi-billing-search.xml@L206` / `@L209` (and `@L239` / `@L242`) — the path
  `{agencyCode}` is bound from `attributes.uriParams.agencyCode` into a flow variable and passed
  straight to the backend (`implementation.xml` L111, L191). It is never compared to the claim.

There is therefore **no legacy comparison rule to preserve**. The comparison rule is a **new
decision**, made here, not a faithful preservation. The triage's guess is not "what the legacy did";
it is what this ADR now decides the target must do, and it is stated as a decision rather than
disguised as heritage.

**Normative rule.** Entitlement is decided by comparing the path `{agencyCode}` against the
caller's `agencyCodes` claim as follows:

1. **Normalise both sides before comparison.** Trim surrounding whitespace from, and
   case-fold (uppercase), both the path `{agencyCode}` and every value in the `agencyCodes` claim.
2. **Exact match after normalisation.** The caller is entitled iff the normalised path value is
   **contained in** the normalised claim list (set membership, one value equal after trim +
   uppercase). This is a normalised exact match — **not** a prefix match, **not** a substring/contains
   match on the raw strings. `List#contains` on the normalised list is the correct primitive.
3. **Malformed claim is fail-closed.** A claim that is not a list (object, scalar, null,
   unreadable) is a **deny** — a claim the service cannot read must not become an allow. This is
   correct and is **not** changed by this rule; DEF-0106 concerns only the case/padding
   false-deny of an *entitled* caller.
4. **Deny-on-empty/absent claim is unchanged.** An absent or empty `agencyCodes` claim is a deny
   (already normative above). Normalisation does not create entitlement where none was claimed.

**Why this rule and not a stricter one.** The legacy served every authenticated caller regardless
of case or padding (it served them regardless of the claim entirely). A strict exact-byte match
would false-deny an entitled internal caller whose token agency code differs only in case or
whitespace from the path — exactly the cutover-breakage risk this ADR's Consequences section
warns against, and exactly the population the exemption list exists to catch piecemeal.
Case-insensitive + trimmed + exact match is the strictest rule that does not false-deny an
entitled caller over a cosmetic difference; it is stricter than the legacy (which had no check)
and lenient enough not to break legitimate callers at cutover. A case-sensitive or
whitespace-sensitive rule is rejected: it would convert a cosmetic token/path mismatch into a
denial, with no consumer inventory to predict the blast (R-019).

**Re-dispatch.** This is a specification ruling, not an implementation choice; the
`billing-edge` `AgencyEntitlementRule` (and the `billing-agency` authoritative enforcer, which
uses the same claim) must normalise both sides (trim + uppercase) before `List#contains`.
Re-dispatch to `java-service-engineer` (billing-edge): the S5 tests
`caseDifferingAgencyCodeIsDenied` and `whitespacePaddedAgencyCodeIsDenied` must flip from
asserting 403 to asserting the caller is **admitted** (not 403); `malformedAgencyClaimFailsClosed`
must stay 403 (fail-closed on an unreadable claim is correct and unchanged). This re-dispatch is
flagged for the architect's S6 dispatch; it is not performed in this act (the architect writes
contracts and ADRs only).

**Status.** This subsection does not close DEF-0106 and does not approve ADR-0037. DEF-0106
remains **open — pending (a) human approval of the assumption-based ADR-0037 (R-003 unresolved),
(b) the java-engineer re-dispatch implementing the normalisation, and (c) the S7 gatekeeper's
traceability check** that the normalised comparison is implemented and the two S5 parity tests
flip. The comparison rule rides the same assumption-based human gate as DEF-0402/DEF-0403; it is
part of the same highest-consequence decision and is not an agent's to make alone. Status of
ADR-0037 remains **"Accepted (pending human approval at the S3 gate)."**

## Traces to

`km:node/N-0021, N-0035, N-0039, N-0040, N-0041, N-0042, N-0051, N-0058, N-0061, N-0063, N-0066` ·
`spec:capability/CAP-002, CAP-010` · `risk:R-003, R-004, R-012, R-019` · `adr:ADR-0013`
