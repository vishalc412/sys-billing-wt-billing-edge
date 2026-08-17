# ADR-0009: There is no transactional or delivery machinery, and none is introduced

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0009-transaction-strategy.md`

## Context

km §9 is unambiguous: no `<try transactionalAction=…>`, no XA, no VM, no JMS, no IBM MQ, no database
anywhere in `src/main/mule`. Every interaction is a synchronous request/response HTTPS call with no
retry and no compensation. The application is read-only: all eight operations are GETs and nothing is
written to any downstream system. Delivery semantics are recorded as `at-most-once` on every HTTP
request config.

This ADR exists to record an absence, because an absence that is never recorded gets re-invented by
someone at S4 who assumes there must have been something.

## Decision

No transactional machinery, no retry template, no outbox, no saga, no `@Transactional` anywhere in
`sys-billing`. Delivery semantics remain at-most-once. The IBM MQ namespace and the
`com.ibm.mq.allclient` shared library are not carried forward (ADR-0040), so that nobody at S4 reads
a JMS client on the classpath as evidence that messaging was once intended.

"No retry" is a **tested property**, not an assumption: the parity suite asserts that a failed
backend call produces exactly one outbound request.

## Rejected alternatives

**B — introduce retry on the backend calls as part of the migration.** Rejected here and deferred to
ADR-0014, where it is also rejected for cutover: all eight operations are GETs and are safely
retryable in principle, but the ESB has no configured timeout at all (R-008), and retrying inside an
unbounded timeout is how a slow backend becomes an outage. Retry is reconsidered only after the
timeouts are correct and measured.

**C — outbox or saga.** Rejected: there are no writes. Recorded so it is not raised again.

## Consequences

+ True to the source; nothing is lost because nothing was guaranteed.
+ Prevents an S4 implementer adding resilience decorations out of habit and then having to explain a
  behaviour difference at S5. Note the pre-existing target-repo code did exactly this (ADR-0043).
− A weakness is knowingly preserved: a transient network blip on any backend call becomes a
  caller-visible error on the first attempt (N-0005 ec).

## Verification

- Integration: a backend returning 500 produces exactly one outbound request (WireMock request count
  assertion) on every backend-calling endpoint.
- Dependency and code check (CI): no `@Transactional`, no `@Retry`, no `RetryTemplate`, no messaging
  client in any module. `DEP-001` asserts this against the built artifact.

## Traces to

`km:node/N-0005, N-0055, N-0060, N-0065, N-0074, N-0082, N-0103` ·
`spec:capability/CAP-005 … CAP-010` · `adr:ADR-0014, ADR-0040, ADR-0043`
