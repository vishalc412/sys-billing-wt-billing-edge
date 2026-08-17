# ADR-0007: SAML assertion acquisition — per-request by default, cacheable by configuration, and STS failure fails the request

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0007-saml-token-acquisition.md`

## Context

`GetSAMLToken` (N-0071) is the first processor of all four backend-calling flows. It reads two
secrets from Thycotic (N-0072) and calls `module-pingfed:generate-saml` (N-0073), an Exchange module
whose implementation is not in the source tree. Whether that module caches the assertion is unknown
(U3, R-006) and **that single unknown sets the latency budget**: without a cache the API makes two
remote calls per request on six of eight endpoints.

Today, a malformed STS response does not raise. The assertion attributes become null and are
dropped, so the **WAS request goes out unauthenticated** (header omitted by `skipNullOn`) and the
**ESB request goes out with an empty assertion element** (N-0019 ec, N-0054 ec, N-0081 ec). The
failure then surfaces to the caller as the *back end's* 401/403 (ADR-0033).

## Decision

Two parts.

1. **Acquisition.** The assertion is minted per request by default. A cache keyed on the service
   account is implemented behind `BackendAssertionProvider`, with TTL configuration
   `billing.saml.cache-ttl` whose **default is zero** — i.e. the cutover behaviour is a strict
   per-request port under either legacy behaviour, and enabling the cache is a configuration change,
   not a code change, once R-006 is answered. When the TTL is non-zero the cache also honours the
   assertion's `NotOnOrAfter`, which the legacy never reads.

2. **Failure.** A failed or unparseable STS response **fails the request** with 502 and a
   `mule.parity.sts_failure` counter. It does **not** send an unauthenticated or empty-assertion
   request to a billing back end. This is a deliberate, recorded divergence from legacy behaviour.

Consumers checked for dependence on the old behaviour (per the ADR rule on corrections): there is no
consumer inventory (R-019) and none could be checked. What was checked instead is the *shape* of the
change: the old behaviour is observable to a caller only as a backend 401/403/500 arriving on a path
where our own credential exchange had already failed. No consumer can be depending on receiving a
billing backend's authentication error as a description of *its own* request. Under ADR-0033 those
statuses are relayed verbatim today, so the caller-visible change is confined to
`401|403|5xx → 502` on a path that fires only when our STS is broken.

## Rejected alternatives

**A — per-request minting with no cache and no failure change (pure literal port).** Rejected in
part: the acquisition half is what we ship (TTL 0), but preserving the "send it unauthenticated
anyway" failure behaviour was rejected. It transmits a request without the credential the backend
was promised, produces a misleading error for the caller, and hides an STS outage as a backend
authorisation problem — the map itself notes the failure surfaces "as an authorisation error from
the backend, not as a token error".

**B — cache by default until shortly before `NotOnOrAfter`.** Rejected as the cutover default: if
the legacy mints per request, caching changes revocation behaviour (a revoked service account keeps
working for the cache lifetime) and introduces shared mutable state into a stateless application, on
the basis of a guess about an opaque module. The mechanism ships; the default does not.

**C — block until the module owner answers R-006.** Rejected: it blocks CAP-004 and therefore six of
eight endpoints on an external team's response time. R-006 is raised in parallel instead.

## Consequences

+ Cutover latency is at worst identical to today's, and the fix for a two-call-per-request profile is
  a config flag rather than a code change.
+ An STS outage becomes visible as an STS outage.
− We knowingly diverge from legacy behaviour on the STS-failure path, with no consumer inventory to
  check against. If some consumer has built alerting on the resulting backend 401 pattern, it changes.
− If the legacy module *does* cache, our TTL-0 default doubles remote calls per request at cutover
  and the regression appears as latency, which is the hardest kind to attribute. The counter
  `billing.saml.mint_count` per request exists precisely to make that visible on day one.
− The cache code ships untested in production configuration (TTL 0). It must still be tested.

## Verification

- Unit: assertion acquired once per request at TTL 0; reused within TTL when non-zero; never reused
  past `NotOnOrAfter`.
- Integration: STS returning a malformed body yields 502 and **no** outbound backend call is made
  (WireMock verify zero interactions). Criterion TOK-002.
- Metric: `billing.saml.mint_count` and `mule.parity.sts_failure` present in the observability
  contract.

## Traces to

`km:node/N-0010, N-0019, N-0071, N-0072, N-0073, N-0054, N-0059, N-0064, N-0081` ·
`spec:capability/CAP-004` · `risk:R-006` · `adr:ADR-0033`
