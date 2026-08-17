# ADR-0041: CORRECT — the Thycotic client gets the real truststore password, after the truststore is inspected

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0041-defect-thycotic-truststore-password-unprefixed.md`
Disposition: **correct, with a mandatory verification step first**

## Context

N-0008: `trustStorePassword` is written as `${truststore.password}` (**unprefixed**) at `global.xml`
line 29 for the Thycotic connection, while the identical value is read as
`${secure::truststore.password}` at line 43. The property exists only inside the secure-properties
file, exposed under the `secure::` prefix, so the unprefixed reference is probably unresolvable or
resolves to the raw ciphertext. Three realities are possible and the map cannot say which: startup
failure (unlikely — the application evidently starts); the reference resolves to literal text and the
Thycotic TLS connection succeeds anyway because a JKS of public certificates opens without integrity
verification; or the property is supplied unprefixed as a deployment property, which is plausible
given three `secret.server.*` properties demonstrably are (R-014).

Reality two is the interesting one: it would mean the Thycotic connection's certificate validation
works by accident.

## Decision

Correct — the Thycotic client is configured with the correctly-resolved truststore password —
**preceded by a mandatory inspection of the bundled truststore** confirming it contains the Thycotic
server's certificate chain (and, per N-0014, both back ends' chains).

The inspection is not optional, and the archaeologist's caution is the reason: correcting this changes
startup behaviour. If the truststore does not actually contain the Thycotic chain, the connection now
**fails at startup**, taking every backend-calling endpoint with it, because CAP-004 gates six of
eight endpoints.

ADR-0039 regenerates the truststore password per environment, which rewrites this reference anyway;
sequencing the two together makes both cheaper.

## Rejected alternatives

**A — reproduce the unresolvable reference.** Rejected: there is no coherent way to preserve a
probably-unresolvable property reference in a Java configuration system. The target either resolves
the value or it does not; "silently resolves to the placeholder text" has no equivalent. Preservation
here is not achievable, only approximated, and approximating it would mean deliberately configuring a
wrong password.

**B — correct without inspecting the truststore first.** Rejected: it is the same decision with its
one avoidable failure mode left in place, and that failure mode is a total outage discovered at
deployment.

## Consequences

+ Certificate validation for the vault connection stops depending on an accident.
+ Consistent with the only other use of the same value.
− If reality three holds (the property is supplied at deployment), this correction is a no-op and the
  inspection was still worth doing.
− The inspection is a manual step with an owner outside the migration team (R-030), and it is on the
  cutover critical path.

## Verification

- CFG-002: the truststore inspection result is recorded as evidence (issuer list), and startup
  succeeds against Thycotic with the configured password in every environment.

## Traces to

`km:node/N-0007, N-0008, N-0014, N-0020, N-0102` · `spec:capability/CAP-004, CAP-012` ·
`risk:R-014, R-030` · `adr:ADR-0008, ADR-0039`
