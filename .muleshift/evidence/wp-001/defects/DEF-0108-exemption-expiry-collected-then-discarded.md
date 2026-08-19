---
id: DEF-0108
title: "Exemption expiry is collected and validated for presence at startup then discarded — a 2020-dated exemption still grants EXEMPT cross-agency PII access indefinitely"
service: billing-edge
severity: high
class: implementation
raised_at_stage: S5
summary: >
  ADR-0037 requires an exemption entry to carry an owner AND an expiry, and (as amended 2026-08-19)
  makes past-expiry deny normative — boundary inclusive (an exemption dated 2026-09-30 is active on
  2026-09-30 and denied on 2026-10-01). StartupConfigurationValidator checks the expiry is PRESENT but
  never that it is in the future. BillingEdgeConfiguration#agencyEntitlementRule projects the
  exempt list down to client ids and throws the owner and the expiry away, so AgencyEntitlementRule
  cannot honour an expiry even in principle. The S5 probe adds a 2020-01-01-dated exemption, startup
  accepts it, and the rule still returns EXEMPT — full cross-agency customer-PII access indefinitely.
reproduction: >
  Add to BillingEdgeProperties.security.agency-entitlement-exempt-clients an exemption for client
  "legacy-batch-client", owner "someone-who-left", expiry LocalDate.of(2020,1,1). Call
  StartupConfigurationValidator.validate(["test"]) — it passes (presence check only). Construct
  AgencyEntitlementRule(Set.of("legacy-batch-client")) and decide("A0421", a CallerContext for
  that client with an empty agencyCodes claim, enforcing=true). The decision is EXEMPT — i.e. an
  exempt caller with no agency entitlement at all keeps cross-agency access to customer PII, years
  after the exemption's expiry. The S5 test asserts this EXEMPT and names DEF-0108 in its
  @DisplayName: "DEF-0108: an exemption that expired years ago still grants access".
evidence:
  - "test:com.westfield.api.billing.edge.s5.S5ProvenanceAndExemptionProbeTest#anExpiredExemptionIsDenied"
  - "test:com.westfield.api.billing.edge.s5.S5ProvenanceAndExemptionProbeTest#anExemptionExpiringTodayIsStillLive"
  - "test:com.westfield.api.billing.edge.s5.S5ProvenanceAndExemptionProbeTest#aLiveExemptionStillGrantsAccess"
  - "evidence:run-s5-billing-edge-r2-e7876edf"
  - "commit:e7876edf4194a32042d19d11a5abdce4447aad3e"
  - "adr:ADR-0037"
traces_to:
  - "km:node/N-0035"
  - "test:com.westfield.api.billing.edge.s5.S5ProvenanceAndExemptionProbeTest#anExpiredExemptionIsDenied"
  - "evidence:run-s5-billing-edge-r2-e7876edf"
status: resolved
blocks_gate: false
triage:
  round: 2
  disposition: >
    Implementation defect. ADR-0037 as amended (2026-08-19, S6) makes past-expiry deny normative and
    the boundary inclusive. The impl collects the expiry and validates its presence at startup, then
    discards it when projecting the exempt list to client ids, so the rule cannot honour an expiry.
    A never-expiring privilege grant to cross-agency customer PII is exactly the outcome the
    mandatory expiry field exists to prevent (the ADR amendment rejects "the date is decoration"
    explicitly). This is an implementation defect against a now-normative requirement — the expiry
    semantics are normative regardless of the enforcement-mode (enforce vs log-only) human decision,
    so DEF-0108 does NOT ride the ADR-0037 assumption-based human gate the way DEF-0402/DEF-0403 do.
    Autonomously fixable by a java-engineer re-dispatch. Round 2 of the bounded loop.

    **Resolution (S5 round-2):** Fix verified green in S5 round-2 (run-s5-billing-edge-r2-e7876edf, commit e7876edf4194a32042d19d11a5abdce4447aad3e) by S5ProvenanceAndExemptionProbeTest#anExpiredExemptionIsDenied + #anExemptionExpiringTodayIsStillLive + #aLiveExemptionStillGrantsAccess (surefire, 9 tests, 0 failures; boundary-inclusive — an exemption dated today is still live, a past-dated one is denied). Status advanced open→resolved by migration-architect S6 reconciliation. Not closed: closure is the S7/human gate's call.
  action: >
    Re-dispatch to java-service-engineer (billing-edge): thread the expiry (and owner) through to
    AgencyEntitlementRule so an exempt client is only EXEMPT while its exemption is live on the
    decision date (boundary inclusive — active through the expiry date, denied the day after),
    matching the ADR-0037 amended semantics and the implemented ExemptClient.isActiveOn behaviour.
    StartupConfigurationValidator must keep rejecting entries without owner/expiry. The S5 test
    S5ProvenanceAndExemptionProbeTest#anExpiredExemptionStillGrantsAccess must flip from asserting
    EXEMPT to asserting DENIED for a 2020-dated exemption at the current date, while a
    non-expired exemption still asserts EXEMPT. (Note: the ADR-0037 amendment's human approval is a
    separate gate item; the expiry-deny semantics are normative independent of that approval.)
  decided_by: migration-architect
---

# DEF-0108: exemption expiry is collected then discarded — exemptions never expire

Severity:  high
Found in:  S5 verify of WP-001, billing-edge
Service:   billing-edge
Criterion: SEC-002 (exemption expiry), ADR-0037
Layer:     domain/security

## What is wrong

The S5 probe `@DisplayName`:

> "DEF-0108: an exemption that expired years ago still grants access"

The test Javadoc records the mechanism:

> "Startup accepts it: the check is 'an expiry is PRESENT', never 'an expiry is in the future'.
> BillingEdgeConfiguration#agencyEntitlementRule projects the list down to client ids and throws
> the owner and the expiry away, so the rule cannot honour an expiry even in principle."

A 2020-01-01-dated exemption for a client with **no agency entitlement at all** still yields
`EXEMPT` — full cross-agency access to customer PII (the named insured's name and address, policy
number, overdue amount, cancellation date — the data ADR-0037's Context names as highest-consequence),
indefinitely.

## Why this is HIGH

A never-expiring privilege grant to cross-agency customer PII. The mandatory expiry field exists
precisely to bound the privilege; collecting it and discarding it makes the field decoration.
ADR-0037's amendment (2026-08-19, S6) makes past-expiry **deny** normative — boundary inclusive —
and explicitly rejects "the date is decoration and the exemption persists." The impl violates the
now-normative semantics. This is a cross-agency PII exposure that persists past the exemption's
recorded expiry. High. Gate blocker.

## Authoritative basis

ADR-0037 (as amended 2026-08-19, S6): "Past the expiry date the caller is treated as **unexempted**
and the deny rule applies. The boundary is **inclusive** … The alternative reading (the date is
decoration and the exemption persists) is rejected — it would make the mandatory expiry field
meaningful." The implemented `ExemptClient.isActiveOn` already does the right thing for the
boundary; the rule must consult it.

## Disposition

Class: **implementation**. The expiry-deny semantics are normative in the amended ADR independent of
the enforcement-mode human decision, so this defect is autonomously fixable (re-dispatch to
java-engineer) and does **not** ride the ADR-0037 assumption-based human gate. Round 2 of the
bounded loop. **Status: resolved** — fix verified green in S5 round-2 (run-s5-billing-edge-r2-e7876edf);
closure is the S7/human gate's call, not S6's.

## Trace chain

`km:node/N-0035` (the agencyCodes claim / exemption model) →
`config.BillingEdgeConfiguration#agencyEntitlementRule` (projects to client ids, discards
owner+expiry) → `domain.security.AgencyEntitlementRule` (cannot honour expiry) →
`test:S5ProvenanceAndExemptionProbeTest#anExpiredExemptionStillGrantsAccess` →
`evidence:run-s5-billing-edge-8f2eec88` · `adr:ADR-0037`

## Related

DEF-0106 (comparison rule unspecified) is the same `AgencyEntitlementRule` neighbourhood, same ADR,
different mechanism (specification, not implementation). DEF-0402 (R1, agency) is the ADR-0037
exemption-list mechanics under-specification now made normative — DEF-0108 is the edge-side
implementation of one of those mechanics (expiry semantics) left unimplemented.