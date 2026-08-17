# ADR-0014: Timeouts are stated as numbers, the ESB keeps its effective value, and no retry is added

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0014-timeout-retry-resilience.md`

## Context

The legacy posture is inconsistent in a way that is invisible from any single file. WAS calls use
`${response.timeout}` = 10 s (20 s in production) with a 45 s idle connection and no retry. **The ESB
calls have no configured timeout at all**: the three `/primaryAccount` endpoints inherit the Mule 4.4
connector default, which the map states as 30 s — *not* the 10/20 s the property files imply (N-0074,
R-008). PingFederate and Thycotic timeouts are not exposed at all.

The agency worklists are the most likely timeout candidate in the application: one call, no paging,
unbounded response size, same 10 s budget as a single-account lookup (N-0060 ec, R-010). Production's
20 s timeout means no latency result from a lower environment is representative (N-0003 ec). There is
no latency, throughput or availability target anywhere (R-001).

## Decision

Timeouts are configuration values with explicit numbers, none inherited:

| Path | Connect | Response | Rationale |
|---|---|---|---|
| WAS (`billing-agency`) | 10 s | **10 s**, 20 s in prod | as today (N-0005) |
| ESB (`billing-account`) | 10 s | **30 s** | the effective legacy value stated as a number (R-008) |
| PingFederate STS | 5 s | **10 s** | previously unbounded; bounded because an STS hang now fails the request (ADR-0007) |
| Thycotic | 5 s | **10 s** | previously unbounded |

**No retry anywhere** (ADR-0009). Tightening the ESB to 10 s is deferred until its latency
distribution is measured (R-008 / R-001); the measurement is a named prerequisite, and until it
exists, 30 s stands.

Because the three modules share one JVM (ADR-0001), `billing-agency` inbound request handling gets
its own bounded executor so that a slow, unbounded worklist cannot exhaust the threads serving the
account endpoints. That is new, and it is the only bulkhead available inside one deployable.

The unpaged worklist itself (R-010) is raised with the WAS backend owner: paging is a backend
capability question, not a migration decision, and it is the only real fix.

## Rejected alternatives

**B — set the ESB timeout to `${response.timeout}` (10 s / 20 s), which the property files always
implied.** Rejected for cutover: it tightens three high-traffic endpoints from an effective ~30 s to
10 s with **no measurement of ESB latency** (R-008, R-001). If any ESB call currently completes
between 10 and 30 seconds, this migration converts a slow success into a failure and does so on the
busiest endpoints. Correct-looking, and the fastest way to cause an incident at cutover.

**C — measure first, then set 10 s.** Adopted as the *route* but rejected as a gate: the measurement
requires production telemetry from the legacy system with an owner and lead time, and blocking the
scaffold on it would stop everything. The decision therefore ships A's effective value stated as a
number, with C as the named path to B.

**D — add retry.** Rejected: every operation is a GET and safely retryable in principle, but retry on
top of a 30 s unmeasured timeout multiplies the worst case, and on the unbounded worklist it is a
plausible way to convert a slow backend into an outage. Retry is reconsidered only after the timeouts
are correct and measured. Note this also reverses a choice the pre-existing target code made
(Resilience4j `@Retry` on outbound calls — ADR-0043).

## Consequences

+ Every timeout in the system is now a stated number in one file, which "inherit the connector
  default" could never survive.
+ The worklists can no longer starve the account endpoints of threads.
− The ESB's 30 s ceiling is preserved, so the highest-traffic endpoints keep their most generous
  timeout and the risk that motivated R-008 is carried forward, deliberately.
− 30 s is copied from the map's statement of a Mule 4.4 default. If that statement is wrong, we have
  pinned the wrong number — and it now looks like a decision someone made rather than an accident.
− Bounding the STS and vault calls is new behaviour. A previously-hanging call now fails fast, which
  changes the failure mode from "slow" to "error" on a path nobody has measured.

## Verification

- Integration: each adapter's configured timeouts asserted from configuration (ESB-003, EXT-006).
- Integration: a backend delaying past the configured response timeout produces the absorbed-failure
  outcome of ADR-0010 and exactly one outbound attempt.
- NFR test: `billing-agency` executor saturation does not increase `/primaryAccount` latency beyond
  its own budget.

## Traces to

`km:node/N-0003, N-0005, N-0008, N-0055, N-0060, N-0065, N-0073, N-0074, N-0082, N-0101` ·
`spec:capability/CAP-005 … CAP-012` · `risk:R-001, R-008, R-010` · `adr:ADR-0007, ADR-0009`
