---
id: DEF-0107
title: "ERR-001-e criterion says redact the cause; accepted ADR-0015 decided to unredact — the criterion is stale, the code is correct"
service: billing-edge
severity: low
class: sanctioned_divergence
raised_at_stage: S5
summary: >
  ERR-001-e says "the cause is redacted to a correlation-id-referenced diagnostic and the full cause
  appears only in the service log", citing ADR-CANDIDATE-0015. The accepted ADR-0015 decided the
  opposite — "the response body is unchanged, including error.cause" — and explicitly rejected
  redacting it (rejected alternative C, deferred to ADR-0042 v2). The implementation follows the
  accepted ADR: the raw cause (internal hostname wassvcu.westfieldgrp.corp and a payload fragment
  1234567890) reaches the caller unredacted, with the correlation id. The criterion was never
  amended and therefore fails against the shipped code. This is a sanctioned divergence: the code
  is correct, the criterion text is stale.
reproduction: >
  Call UnhandledFailurePresenter#present(failure, "corr-1234", null) where the failure's cause is an
  IllegalStateException("connect timed out to wassvcu.westfieldgrp.corp:9443 while reading
  <acct>1234567890</acct>"). The emitted body's errorCause contains "wassvcu.westfieldgrp.corp" and
  "1234567890" verbatim, and correlationId is "corr-1234". The S5 test asserts this and names
  DEF-0107 in its AssertJ description: "ADR-0015 SANCTIONS this; the criterion contradicts the
  accepted ADR — DEF-0107."
evidence:
  - "test:com.westfield.api.billing.edge.s5.S5FailurePresentationProbeTest#theRawCauseIsReturnedToTheCallerUnredacted"
  - "test:com.westfield.api.billing.edge.s5.S5AuditFunnelIT"
  - "evidence:run-s5-billing-edge-8f2eec88"
  - "adr:ADR-0015"
traces_to:
  - "km:node/N-0069"
  - "test:com.westfield.api.billing.edge.s5.S5FailurePresentationProbeTest#theRawCauseIsReturnedToTheCallerUnredacted"
  - "evidence:run-s5-billing-edge-8f2eec88"
status: open
blocks_gate: false
linked_adr: "adr:0015"
triage:
  round: 2
  disposition: >
    Sanctioned divergence. The code follows the accepted ADR-0015 (body unchanged, including
    error.cause; logs masked, response body not). The ERR-001-e criterion text was written from the
    ADR candidate and never amended to match the accepted ADR, so it contradicts the shipped code.
    The code is correct; the criterion is stale. No code change. The criterion text should be
    corrected at S2 to match ADR-0015 (redaction was rejected alternative C, deferred to ADR-0042
    v2). The S5 parity test that asserts the unredacted observation stays as-is — it is the
    sanctioned behaviour. Round 2 of the bounded loop.
  action: >
    Return the criterion text ERR-001-e to S2 for correction to match the accepted ADR-0015
    ("response body is unchanged, including error.cause; logs are masked, the body is not"). No
    java-engineer re-dispatch; no ADR amendment (ADR-0015 already decided this). The parity test
    S5FailurePresentationProbeTest#theRawCauseIsReturnedToTheCallerUnredacted is the evidence and
    stays, asserting the sanctioned (unredacted) behaviour.
  decided_by: migration-architect
---

# DEF-0107: ERR-001-e criterion (redact) is stale vs accepted ADR-0015 (unredact)

Severity:  low
Found in:  S5 verify of WP-001, billing-edge
Service:   billing-edge
Criterion: ERR-001-e
Layer:     failure presentation (specification defect, sanctioned divergence)

## What is wrong

The S5 probe `@DisplayName`:

> "ERR-001-e: the raw cause reaches the CALLER unredacted, exactly as accepted ADR-0015 requires"

The test Javadoc records the contradiction:

> "ERR-001-e says: 'the cause is redacted … and the full cause appears only in the service log',
> citing ADR-CANDIDATE-0015. The accepted ADR-0015 decided the opposite — 'the response body is
> unchanged, including error.cause' — and explicitly rejected redacting it (rejected alternative C,
> deferred to ADR-0042 v2). The implementation follows the accepted ADR. The criterion, never
> amended, therefore fails against the shipped code. That is a specification defect, not an
> implementation one, and DEF-0107 records it as such."

The emitted `errorCause` contains the internal hostname `wassvcu.westfieldgrp.corp` and the payload
fragment `1234567890` verbatim, with the correlation id `corr-1234`.

## Why this is sanctioned divergence, not an implementation defect

The code is correct — it implements the accepted ADR-0015. The criterion ERR-001-e is stale (it
was written from the ADR candidate, which the architect rejected). ADR-0015 is the sanctioning
decision; redaction was a rejected alternative. So the divergence is sanctioned, and the action is
to correct the criterion text at S2, not to change the code.

## Severity rationale

**Low.** No code change, no security regression (the accepted ADR-0015 decided this deliberately —
logs are masked, the body is not, preserving the legacy response shape). Only the criterion text is
out of date.

## Disposition

Class: **sanctioned_divergence** (sanctioned by ADR-0015). No code change. The criterion text
ERR-001-e should be corrected at S2 to match ADR-0015. Round 2 of the bounded loop. Status remains
**open** (the S2 criterion correction has not been performed).

## Trace chain

`km:node/N-0069` (errorCause present with a null value, never omitted — the legacy failure shape) →
`application.failure.UnhandledFailurePresenter` (body unchanged per ADR-0015) →
`test:S5FailurePresentationProbeTest#theRawCauseIsReturnedToTheCallerUnredacted` →
`evidence:run-s5-billing-edge-8f2eec88` · `adr:ADR-0015`