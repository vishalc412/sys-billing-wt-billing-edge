# ADR-0008: Secrets stay in Thycotic at cutover, fetched per request with a short TTL

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0008-secrets-management.md`

## Context

Three mechanisms exist today: Thycotic Secret Server at runtime for exactly two values
(`billing.username`, `billing.password`, N-0008/N-0020/N-0072) with the stated property that
"credential rotation does not require a redeploy"; Mule secure properties encrypting exactly one
value (`truststore.password`, whose ciphertext is byte-identical across all six environments —
ADR-0039); and four deployment-time properties that exist in no file in the repository (U11, R-014).
Nothing in the repository contains a plaintext credential (km §11), and that must remain true.

`GetSAMLToken` also leaves the service-account username and password in flow variables for the rest
of the request (N-0071 ec).

## Decision

**Thycotic remains the source of truth at cutover**, read through a `CredentialProvider` in
`billing-edge` with a short TTL cache (`billing.credentials.cache-ttl`, default 60s) so that
rotation still takes effect without a redeploy while the vault call rate stays bounded.

Three supporting rules:

- Credentials are held in a request-scoped value object that is cleared after the token exchange and
  is never placed in MDC, in a log field, or in any object a logger can serialise (N-0071 ec).
- `truststore.password` is regenerated **per environment** at migration (ADR-0039), and the legacy
  `${secure.key}` is not carried forward under any circumstance.
- Platform-native secret mounting (files or environment variables with reload) is the **stated target
  state** and is adopted as soon as R-015 (deployment model) is answered. It is not specified now
  because specifying it against an unknown deployment model is guessing.

## Rejected alternatives

**A — fetch from Thycotic once at startup and cache for the process lifetime.** Rejected: it breaks
the explicitly recorded operational property that credential rotation takes effect without a
redeploy (N-0020). It has the attractive property of turning a vault outage into a deterministic
startup failure, and if the operations owner later states that rotation-without-redeploy is not
actually required, A is simpler and better.

**C — migrate the credentials to the platform secret manager now.** Rejected as a cutover
decision: it requires the billing service account to be provisioned into a second store with an
owner keeping the two in sync during transition, and it depends entirely on a deployment model that
is unknown (U14, R-015). It remains the target state.

## Consequences

+ Both stated properties are preserved: no plaintext in the artifact, rotation without redeploy.
+ Vault call rate is bounded rather than unknown.
− A vault outage remains a partial, per-request failure. That is probably what happens today, but
  nobody can confirm it (R-007), so we are preserving a behaviour we have not observed.
− A 60-second credential cache means a rotated credential can still be used for up to a minute. That
  is a deliberate weakening of "immediate rotation" in exchange for bounded call volume, and it must
  be stated to whoever owns credential rotation.
− The runtime network dependency on Thycotic stays in the request path — the single biggest
  availability improvement available in this migration is deferred to R-015.

## Verification

- Integration: two requests within the TTL cause one Thycotic call; after TTL expiry, two.
- Unit: the credential object is cleared after token exchange; a serialisation test asserts the
  credential type has no accessible getter for the password after clear.
- SAST/CI: no plaintext credential in the repository; secret scanning gate.
- CFG-002 criteria.

## Traces to

`km:node/N-0003, N-0007, N-0008, N-0020, N-0071, N-0072, N-0102` ·
`spec:capability/CAP-004, CAP-012` · `risk:R-007, R-014, R-015` · `adr:ADR-0039, ADR-0041`
