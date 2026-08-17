# ADR-0035: CORRECT — the audit record is written after the final status is known

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0035-defect-catchall-logs-stale-status.md`
Disposition: **correct (log-stream change only), with the lost signal replaced**

## Context

N-0022: in the catch-all handler, `responseLogFlow` runs **before** `sapi-common-errorhandler`, so on
the generic failure path the audit record captures the status from before the error handler set it —
usually unset. `responseStatusCode` is written from `vars.httpStatus` with **no default**, so it is
logged as `null`. Seven of the eight call sites run after the status is set; only the catch-all runs
before. The map states plainly: *"swapping the two flow-refs would fix the log and change nothing
else."*

The consequence is confined to the audit log — but it means the audit trail has no record of what the
caller was told on the API's least understood failure path.

## Decision

Correct. The audit record is completed **after** the final status is determined, on all eight paths,
so the record records the outcome.

Two provisions:

- `responseStatusCode` gets an explicit sentinel when the status is genuinely unknown, consistent
  with the `EMPTY` convention used by every neighbouring field, instead of a bare `null`.
- The signal that the null value accidentally provided — "this request went through the catch-all
  path" — is **replaced deliberately** with an explicit boolean field `unhandledFailure` on the audit
  record, so any downstream control keyed on "status is null" can be rebuilt on something that means
  what it says.

**Consumers checked:** the consumer of this change is the audit log stream, and **no compliance owner
has been identified** (R-021). No consumer was checked, and that is a gap. The mitigation is the
replacement field: any control that today counts null statuses can be re-pointed at
`unhandledFailure` without losing information. This correction must be named in the cutover
communication to whoever owns log analytics — identifying that person is a gate item.

## Rejected alternatives

**A — preserve the ordering and therefore the null/stale status.** Rejected: it requires the
implementer to *deliberately* order the code wrongly, which a future maintainer will "fix" unless it
is loudly commented, and the thing being preserved is an audit record that fails to record the
outcome on the path where the outcome is least understood. It is also the only defect in the register
where the map itself states the fix changes nothing else.

**Correcting without the replacement field.** Rejected: it silently removes a usable signal. The
signal is accidental, but accidental signals are exactly what downstream controls get built on.

## Consequences

+ The audit record records the outcome. Eight of eight call sites follow one rule.
+ The catch-all path remains identifiable, on purpose rather than by accident.
− The audit log content changes on the failure path. If a dashboard filters on a null
  `responseStatusCode`, it breaks — and nobody has been asked, because there is nobody identified to
  ask (R-021).
− A new field appears in the audit record. Adding a field is usually safe; on a stream someone may be
  parsing positionally, it is not guaranteed to be.

## Verification

- Integration: an unhandled failure produces an audit record whose `responseStatusCode` equals the
  status actually returned, and whose `unhandledFailure` is true (AUD-004, ERR-003).
- Unit: the sentinel is emitted, not a bare null, when the status is genuinely undetermined.

## Traces to

`km:node/N-0016, N-0022, N-0023, N-0030, N-0048, N-0049` ·
`spec:capability/CAP-001, CAP-003, CAP-011` · `risk:R-021` · `adr:ADR-0016, ADR-0017`
