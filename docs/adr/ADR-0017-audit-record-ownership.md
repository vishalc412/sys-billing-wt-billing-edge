# ADR-0017: The audit record is a contract on fields; the implementation is consolidated, the rules are not reconciled

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0017-audit-record-ownership.md`

## Context

CAP-003 is the only capability here with an arguably regulatory obligation. The record carries entry
timestamp, the `UWSAPI-REQRES` marker, API identity, both correlation ids, the raw URI and the masked
template, subject, email, client id, impersonating actor, agency entitlements, and at exit the
response status and elapsed time (N-0049, N-0051).

Four recorded inconsistencies: the per-endpoint projection is duplicated verbatim five times
(N-0036); the two log streams disagree about impersonation (ADR-0036); the three `/primaryAccount*`
endpoints emit no endpoint record at all (N-0045 ec); and the console is not audited (N-0031 ec).
Failure semantics also differ from everything else: `setRequestResponse` and `responseLogFlow` are
`<flow>`s, so **a failure building the audit record fails the whole request** before any business
logic runs — the opposite of the `on-error-continue` posture everywhere else, and almost certainly
deliberate. None of this has any test coverage (R-020).

## Decision

- The audit record is a **contract on its fields and sentinels**: same field names, same `EMPTY`
  string sentinel, same empty list for absent `agencyCodes`, same masked-URI rule, same
  `UWSAPI-REQRES` marker.
- The **implementation is consolidated to one** builder in `billing-edge`. The five verbatim copies
  become one class with the URI template as a parameter. No emitted value changes.
- The four inconsistencies are **preserved**: the two impersonation definitions stay (ADR-0036), the
  three `/primaryAccount*` endpoints still emit no endpoint record, the console is still unaudited
  (ADR-0038 does not add auditing).
- **Audit failure still fails the request.** This is stated explicitly because a Spring
  implementer's instinct is to make logging non-fatal, and that instinct would silently remove an
  audit guarantee.
- Reconciliation of the rules is gated on R-021 (no compliance owner has been identified).

## Rejected alternatives

**A — preserve all four inconsistencies *and* the five copies.** Rejected: duplicating a rule five
times changes no emitted value but guarantees future drift; the legacy's own impersonation defect
(ADR-0036) arose exactly this way. Consolidation is invisible to every consumer of the log stream.

**C — consolidate and reconcile: one impersonation definition, endpoint records for all eight
endpoints.** Rejected for cutover: it changes audit output that compliance may already depend on
(the map says this in as many words at N-0052), and adding records for three endpoints changes log
volume and any report that counts records. It needs a compliance owner, and R-021 has not produced
one.

## Consequences

+ One masking implementation, tested once, instead of a rule with no test at all.
+ No emitted audit value changes at cutover, so any downstream control keeps working.
+ The audit-failure-fails-request property is explicit and tested rather than accidental.
− Three of eight endpoints remain absent from one of the two log streams, deliberately.
− Two disagreeing impersonation definitions ship (ADR-0036), which is a poor position to defend to an
  auditor and is now a documented choice rather than an inherited accident.
− Consolidation means a bug in the single builder affects all five endpoints at once. The trade is
  five copies drifting versus one copy failing; the test coverage that never existed is what makes
  the second acceptable.

## Verification

- Unit: audit builder over the recorded field set, including every sentinel and the masking rule
  (AUD-001, AUD-002, AUD-003, AUD-004).
- Integration: a failure inside the audit builder fails the request before any backend call
  (AUD-001).
- Parity: PAR-002 captures the legacy audit record for comparison; a field-level difference is a
  defect routed by S6.

## Traces to

`km:node/N-0009, N-0015, N-0022, N-0031, N-0036, N-0045, N-0048, N-0049, N-0050, N-0051, N-0052` ·
`spec:capability/CAP-003` · `risk:R-020, R-021` · `adr:ADR-0035, ADR-0036, ADR-0038`
