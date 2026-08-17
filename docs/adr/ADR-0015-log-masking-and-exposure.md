# ADR-0015: Logs are masked; the response body is not changed

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0015-log-masking-and-exposure.md`

## Context

`json.data.mask.fields`, `json.data.disable.fields`, `ceh.data.mask.fields` and
`ceh.data.disable.fields` are defined but **empty in all six environments including production**
(N-0009 ec, N-0101 ec). Nothing is masked. What is therefore written to the production log: the full
SOAP request **including the SAML assertion**, at seven sites, on every backend-calling flow; full
fault payloads at four sites; and the account identifiers inside those envelopes (km §6.7).

Separately, `error_Flow` returns `error.cause` — the raw exception cause, which "can include internal
hostnames, stack context and fragments of the SOAP payload" — **to the API caller** (N-0069 ec).

Against that, the audit path has a deliberate, well-designed masking rule: any path segment that is
not purely alphabetic becomes `X`, so account, policy and agency numbers never reach the audit log
(N-0051, N-0052).

## Decision

**Mask on the logging path; change nothing a consumer can observe.**

- The `wsse:Security` element and its contents are never logged. The SAML assertion is replaced with
  `[REDACTED]` in every log statement, at every level.
- Account numbers, policy numbers and agency codes are masked in logged payloads using the same
  masking implementation the audit trail uses (ADR-0017), so one rule governs both streams.
- Full envelopes may be logged at `DEBUG` only, and `DEBUG` is off in production by configuration
  and asserted by a startup check.
- Service-account credentials never enter a log field, MDC, or a serialisable object (ADR-0008).
- The **response body is unchanged**, including `error.cause`. Removing it is a contract change and
  belongs to ADR-0042 / ADR-0012, not to a logging decision.

## Rejected alternatives

**A — reproduce today's logging exactly, unmasked.** Rejected: it would ship a
SAML-assertion-in-plaintext-log exposure into a new system as a deliberate, documented act. That is a
materially different position from inheriting one, and there is no consumer of the API who can even
observe the difference.

**C — also stop returning `error.cause` to the caller.** Rejected *here*, not on the merits: it is
the more serious of the two exposures (a response body leaves the estate; a log does not), but it is
a consumer-visible contract change with no consumer inventory (R-019), and it overlaps ADR-0042.
Changing the error body twice would be wasteful. It is decided in ADR-0042's v2 frame.

## Consequences

+ A credential-in-logs exposure is removed with zero consumer coordination — the rare security fix
  that costs nothing on the wire.
+ One masking implementation for both the audit trail and payload logging.
− Support loses the full envelope from production logs. If incident triage depends on it, that
  workflow changes and nobody has been asked. The `DEBUG` route exists but is off in production by
  decision.
− The more serious exposure — internal hostnames and payload fragments returned to callers in
  `errorCause` — is knowingly left in place at cutover.

## Verification

- Unit: log-masking function over a full SOAP envelope fixture asserts no assertion material and no
  identifier survives.
- Integration: with `DEBUG` disabled, a backend call produces no log line containing the assertion.
- Startup check: production profile refuses `DEBUG` on the payload loggers (CFG-001).

## Traces to

`km:node/N-0009, N-0051, N-0052, N-0053, N-0058, N-0063, N-0068, N-0069, N-0071, N-0079, N-0083,
N-0101` · `spec:capability/CAP-003, CAP-004, CAP-011` · `risk:R-019` · `adr:ADR-0042`
