# ADR-0010: Backend failures are absorbed exactly as today and instrumented; our own mapper failures are not

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0010-error-handling-parity.md`

## Context

Every backend-calling flow uses `on-error-continue` (N-0057, N-0062, N-0067, N-0086, N-0090,
N-0097). km §6.1: **no failure from any implementation flow ever propagates**; the catch-all and
global handlers are effectively unreachable for business failures, and **the HTTP status is the only
signal**. The caller-visible outcome varies by endpoint: 200 with `[]` on a fault for transactions
(N-0090), 204 for `/primaryAccount` and escrow, the backend's status via `error_Flow` elsewhere.

Crucially, these handlers are attached to the flow, not to the HTTP request, so they also catch
**DataWeave mapping errors** (N-0086 ec). A mapping bug is therefore reported to the caller as a
backend error carrying the backend's status code.

## Decision

Two rules, implemented once in `platform-errors` as `AbsorbedFailureHandler` and used by all three
modules.

1. **Failures originating downstream** (transport, HTTP status, SOAP fault, response parse) produce
   the same status and the same body as the legacy, per endpoint, unchanged. Each absorbed path
   additionally emits `mule.parity.swallowed_error` tagged with the capability and the error class,
   and a structured WARN/ERROR log carrying the correlation id and the legacy log code where one
   exists (`SB-PA-C`, `SB-PAT-C`).

2. **Failures originating in our own code** — mapping, projection, normalisation — return **500**
   and are never absorbed. This is a deliberate, narrow divergence: in the legacy a mapper failure
   and a backend failure are indistinguishable to the caller, and preserving that would let our own
   defects masquerade as the back end's during precisely the window when we most need to see them.

`vars.httpStatus` is never reset in the legacy (N-0022 ec, N-0023 ec). The target sets the status
explicitly on every path; the stale-status effect is reproduced **only** where a captured baseline
demonstrates it (R-024 tracks the observation).

## Rejected alternatives

**A — reproduce the absorption silently, with no instrumentation.** Rejected: it carries an
invisible-failure design into a system that has the observability to do better at near-zero cost, and
it hides our own defects at cutover.

**B — surface everything as 5xx.** Rejected: it breaks every consumer currently receiving 200/204
(R-019, no inventory), and it cannot distinguish a swallowed error from the *intended* 204-on-no-
account contract (N-0085), so it would also change behaviour that is not a defect at all.

**Absorbing our own mapper failures too (strict parity).** Rejected as described above. The
divergence is recorded here rather than discovered at S5 as an unexplained status difference.

## Consequences

+ The cutover contract is preserved on every downstream-failure path; no consumer change required.
+ Swallowed failures become measurable for the first time, which is the evidence ADR-0032 needs
  before anything is corrected.
+ Our own bugs surface as 500s during cutover instead of being attributed to the back end.
− The system knowingly ships incorrect error semantics on six paths.
− The counter will fire more than expected at go-live and needs a triage owner. Naming one is a
  cutover-readiness item, not an engineering task; today it has no owner, which per the ADR rules
  means it may not happen.
− "Originating in our own code" is a classification made in code. Get it wrong and a backend parse
  failure becomes a 500 (a visible divergence) or a mapper bug becomes a silent absorption. The
  boundary is drawn at the adapter: anything thrown by `adapter/out` while reading the wire is
  downstream; anything thrown by `domain` or `application` is ours.

## Verification

- Integration per endpoint: backend 500, backend fault, and backend timeout each produce the legacy
  status and body, plus one counter increment (ACC-007, TXN-003, ESC-003, EXT-005, AGY-007).
- Integration: a mapper throwing produces 500 and **no** absorption (ERR-001, ERR-002).
- Parity: golden replay for each absorbed path where a baseline exists (ADR-0021).

## Traces to

`km:node/N-0012, N-0022, N-0023, N-0030, N-0057, N-0062, N-0067, N-0068, N-0084, N-0086, N-0090,
N-0097` · `spec:capability/CAP-011` · `risk:R-019, R-024` · `adr:ADR-0032, ADR-0033`
