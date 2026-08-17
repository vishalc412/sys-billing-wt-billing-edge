# ADR-0033: PRESERVE — the back end's status is still relayed verbatim, with a failure-origin header added

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0033-defect-upstream-status-relayed-verbatim.md`
Disposition: **preserve (bug-compatible) + additive diagnostic**

## Context

N-0068: the upstream back end's HTTP status is passed through verbatim to the API consumer. A backend
404 becomes a 404 from this API even though the requested resource exists; a backend 401/403 becomes a
caller-facing authentication error even though the caller's own token was fine — the failure is *our*
service account. A malformed PingFederate response does not raise today, so an STS problem manifests
as a 401 to the caller. `error_Flow` is reached from five `on-error-continue` blocks, so this applies
to every endpoint except `/info` and the console. The derivation is order-dependent: payload first,
status second (N-0068 ec).

## Decision

Preserve the status relay exactly, including the 500 default when the failure was not an HTTP
failure. Add one **additive** diagnostic that no legacy response carries: the response header
`X-Billing-Failure-Origin: upstream-esb | upstream-was | upstream-sts | gateway | this-service`, set
on every non-2xx produced by the failure path.

The order dependency is preserved as an explicit two-step in `platform-errors` with a comment naming
N-0068, because reordering silently breaks the status derivation.

Note that ADR-0007 removes the largest source of misleading 401s: an STS failure now fails with 502
instead of producing an unauthenticated backend call that returns 401.

## Rejected alternatives

**C — map backend 401/403/404 to 502 while relaying 5xx.** This was the analyst's recommendation and
the closest call in this ADR. Rejected for cutover under the ADR rule on corrections: it changes the
status on the three most-used branch points with **no consumer inventory** (R-019), and a rule with
exceptions will be wrong for some backend code nobody anticipated, because there is no fault taxonomy
(R-017). The candidate itself names the fallback for exactly this situation — "if they cannot be
named before cutover, fall back to A + D" — and they cannot be named.

**B — map all backend failures to 502/504.** Rejected: honest, and it changes every failure path at
once against an unenumerated consumer set. It is the v2 answer alongside ADR-0042.

**D alone without the header (pure preservation).** Rejected: the header costs nothing, breaks
nothing, and gives support the one piece of information that resolves the misdirection — which system
actually failed.

## Consequences

+ Zero status change; any consumer with retry or alerting logic built on the observed distribution
  keeps working.
+ Support and operations can attribute a failure to the right system without a code change.
− **Misleading statuses ship deliberately.** A caller receiving 401 will re-authenticate, fail again,
  and raise a ticket against the wrong system. Adding a header does not fix that unless someone reads
  it, and consumers must be told about it for it to help.
− The API's error rate remains uninterpretable: a 4xx may mean the caller erred or may mean our
  service account expired.
− ADR-0007 shrinks the problem but does not remove it: a genuine backend authorisation failure still
  reaches the caller as 401.
− The follow-up to option C has **no owner**, which per the ADR rules means it may never happen. That
  is stated here rather than implied.

## Verification

- Integration: backend 404, 401, 403 and 500 each relay verbatim, and each carries the correct
  `X-Billing-Failure-Origin` value (ERR-002).
- Unit: a non-HTTP failure yields 500 with origin `this-service`.
- Unit: the payload-then-status ordering is asserted (a regression test for the ordering itself).

## Traces to

`km:node/N-0019, N-0054, N-0057, N-0062, N-0067, N-0068, N-0069, N-0070, N-0081, N-0084, N-0086,
N-0090, N-0097` · `spec:capability/CAP-011` · `risk:R-017, R-019` ·
`adr:ADR-0007, ADR-0010, ADR-0015, ADR-0042`
