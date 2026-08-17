# Manual follow-up: verify the PingFederate STS request shape

`PingService` (`src/main/java/com/westfiled/api/billing/soap/PingService.java`) reconstructs the
SAML assertion request that Mule's `module-pingfed:generate-saml` operation used to send. That
connector is closed-source (`pingfed-westfieldgrp-extension`, pulled from the Anypoint Exchange in
`archive/sapi-billing/pom.xml`), so its exact wire format isn't available from the Mule source.

What was implemented instead: a standard WS-Trust 1.3 `RequestSecurityToken` call with a
`wsse:UsernameToken` (username/password from `app.saml.username` / `app.saml.password`, sourced
from Thycotic Secret Server in the original flow) against `https://{app.saml.host}:{app.saml.port}
{app.saml.sts-path}` (default path `/idp/sts.wst` — **guessed**, not confirmed). This produces the
same `Envelope.Body.RequestSecurityTokenResponseCollection.RequestSecurityTokenResponse
.RequestedSecurityToken.Assertion` shape the Mule DataWeave scripts read from
`vars.samlResponse`, so `PingService` extracts the `<Assertion>` element the same way.

Before relying on this in a real environment:

1. Confirm the actual STS endpoint path exposed by PingFederate for this integration
   (`idpa1-test.westfieldgrp.corp:9543`, `sso.westfieldgrp.corp:9543` in prod) — `/idp/sts.wst` is
   an assumption based on common PingFederate STS conventions, not confirmed against config.
   Provide it via `PING_STS_PATH` if it differs.
2. Confirm PingFederate accepts a `UsernameToken` credential for this token type/policy — the
   Mule connector may have used a different credential/binding.
3. Confirm the `wsa:Address`/`AppliesTo` value expected matches `saml.wsa.address`
   (`urn:sts:mulesoft:unt:to:saml:nonprod` / `:prod`) unchanged from the Mule config.
4. Load-test the `samlToken` Resilience4j circuit breaker/retry thresholds
   (`resilience4j.circuitbreaker.instances.samlToken` in `application.yaml`) against the STS's
   real latency/error characteristics.
