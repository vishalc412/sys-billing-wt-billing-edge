# ADR-0013: The service validates the bearer token itself, and does not assume a gateway

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0013-inbound-authentication-placement.md`
Assumption-based: **yes — depends on R-003, which is unresolved.**

## Context

The application implements no inbound authentication at all (N-0021). The RAML's `secured` trait
requires a bearer token on every operation including `/info`; enforcement is attached to the API
instance in API Manager via autodiscovery (N-0004). **The policy set was never exported**
(`inventory.policies[]` is empty — U5, R-003), so the entire inbound security model is invisible.

What is inferable: every logging expression reads `authentication.properties.userProperties.*`, a
binding that exists only when a token-enforcement policy has populated it, and the claims consumed
are `clientId`, `sub`, `email`, `act.sub`, `act.email`, `actSub`, `actEmail`, `agencyCodes`
(N-0021 ec). That is strong evidence of an OAuth/OIDC token-enforcement policy — but the exact
policy, its scopes and its error responses are unknown.

R-003 was raised as a blocking item for S3. It has not been answered. This ADR therefore decides
under uncertainty rather than waiting, and says so.

## Decision

`billing-edge` validates the bearer token itself, as a Spring Security OAuth2 resource server, with
issuer and JWKS supplied per environment. Claims are mapped into a typed `CallerContext` in
`platform-spi` carrying subject, email, client id, actor (both representations, ADR-0036) and
`agencyCodes`. Every endpoint including `/info` requires a valid token, matching the `secured` trait.

Two provisions for the unknown:

- **Compatibility mode.** If the target platform turns out to run a gateway that validates tokens and
  injects claims, service-side validation remains on (defence in depth, redundant and harmless) and
  the claim extraction reads the token, not injected headers. Trusting injected headers is not
  offered as a mode, because a service that trusts headers is bypassable the moment it is reachable
  directly — which today it is, on `0.0.0.0:8081` with no auth (N-0013 ec).
- **The rejection body shape is a documented divergence.** Today a rejected token is answered by API
  Manager with a body nobody has seen. The target answers with the RFC 9457 problem detail from
  `platform-errors`. When R-003 arrives, matching that shape is a contract defect routed to the
  architect, not a re-decision.

## Rejected alternatives

**A — keep enforcement external and have the service trust injected claims.** Rejected: it preserves
today's split of responsibility, but it requires a target-platform gateway that nobody has confirmed
exists (R-015) and it leaves the service trivially bypassable if reached directly. For an API that
serves customer billing data and an agency worklist with no entitlement check (ADR-0037), that is not
a posture worth migrating.

**C — validate in the service but reproduce the gateway's rejection response shape.** Rejected as
undeliverable: it cannot be specified until R-003 is answered, so choosing it would block the
decision on the same missing artifact.

**Waiting for R-003 before deciding.** Rejected: R-003 was assigned and has not returned. Blocking
S3 on it stops all eight endpoints; deciding with a stated assumption and a defined correction route
does not.

## Consequences

+ The service is safe when reached directly, which is the only assumption we can actually verify.
+ Claims become a typed, testable object rather than an invisible runtime binding — and the same
  object makes ADR-0037's entitlement check implementable and testable.
+ ADR-0036's impersonation rules get a single typed source.
− **This is an assumption-based decision.** If the API Manager policy set turns out to do client-id
  enforcement, rate limiting or IP allowlisting as well, none of that is reproduced and the scope of
  this migration grows at the worst possible moment.
− The error body for a rejected token changes. That is consumer-visible on the auth failure path and
  we have not checked a single consumer (R-019).
− Issuer, JWKS URI and accepted audiences must be supplied per environment; if they are wrong, every
  request fails at cutover. This is loud rather than silent, which is the acceptable failure mode.

## Verification

- Integration: no token, expired token, wrong issuer, wrong audience each produce 401 with the
  RFC 9457 body; a valid token populates `CallerContext` with all eight claims (SEC-001).
- Integration: `/info` requires a token (INF-001, N-0043 ec).
- Contract: `contracts/billing-edge/openapi.yaml` declares the bearer security scheme on every
  operation of every module.

## Traces to

`km:node/N-0004, N-0013, N-0021, N-0035, N-0036, N-0043, N-0051` ·
`spec:capability/CAP-002, CAP-003` · `risk:R-003, R-015, R-019` · `adr:ADR-0037`
