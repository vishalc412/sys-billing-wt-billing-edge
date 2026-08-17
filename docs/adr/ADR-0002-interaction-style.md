# ADR-0002: The system stays synchronous end to end

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0002-interaction-style.md`

## Context

All eight operations are synchronous `GET`s and every backend interaction is a synchronous
request/response HTTPS call (km §1, §9). There is no queue, no scheduler, no publisher and no
consumer. The `ibm-mq` namespace and `com.ibm.mq.allclient` shared library are declared and entirely
unused (N-0103, `dead_code: true`).

Two facts make this worth deciding rather than defaulting: `GetSAMLToken` (N-0071) is the first
processor of all four backend-calling flows and may be one or two remote calls per request (U3,
R-006); and the three `/primaryAccount` endpoints each make a full, identical backend round trip for
the same account (N-0018).

## Decision

The target is synchronous end to end. No broker, no events, no scheduler, and **no cache in front of
any backend call**. Delivery semantics remain at-most-once with no retry (ADR-0009). The only
caching permitted anywhere in the request path is the SAML assertion cache of ADR-0007, which
defaults to a zero TTL and is therefore off unless deliberately enabled.

The triple round trip for a caller wanting all three `/primaryAccount` views is preserved. It is
addressed, if at all, by ADR-0014 (bounding backend exposure with timeouts), not by caching.

## Rejected alternatives

**B — synchronous API with a cache in front of the ESB enquiry.** Rejected: a billing balance is a
moving figure and the knowledge map records no staleness tolerance anywhere (N-0082 states plainly
"no retry, no fallback and no cache"). Introducing one is a business decision about correctness
disguised as a performance tweak, and nobody has been asked. It becomes reconsiderable only when
R-001 produces a throughput figure *and* the business states a staleness tolerance in seconds — the
statement, not the number, is the gate.

**C — pre-computed daily feeds for the agency worklists.** Rejected: "today" is decided entirely by
the back end, whose clock and timezone are unknown (U10, R-011). You cannot pre-compute a day
boundary you cannot observe. It also converts a read API into a stateful pipeline, which is far
outside migration scope.

## Consequences

+ Exact parity of delivery semantics; nothing to reproduce and nothing to invent.
+ No new infrastructure, no ordering or idempotency questions for a read-only API.
+ `contracts/` contains no `asyncapi.yaml`, and that absence is now a recorded decision.
− The known inefficiency (three backend round trips for one account) ships unchanged, and with it
  whatever load it places on the ESB — a load nobody has measured (R-001).
− If the ESB is in fact saturated by that pattern, we will discover it in production, because we
  have chosen not to measure it before cutover.

## Verification

- Dependency check (CI): no messaging client, broker driver or scheduler dependency appears in the
  built artifact — the same mechanical check that verifies ADR-0040 (task `DEP-001`).
- Integration test: three sequential calls to the three `/primaryAccount` endpoints produce three
  distinct backend requests against the WireMock ESB stub (no memoisation).

## Traces to

`km:node/N-0103, N-0018, N-0071, N-0079, N-0060, N-0065` · `spec:capability/CAP-008, CAP-010` ·
`risk:R-001, R-006, R-011`
