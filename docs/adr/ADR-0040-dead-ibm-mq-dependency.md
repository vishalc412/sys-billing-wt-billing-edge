# ADR-0040: The IBM MQ namespace and shared library are not carried forward

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0040-defect-dead-ibm-mq-dependency.md`
Disposition: **correct — dead code removed; no behaviour exists to preserve**

## Context

N-0103 is the only node in the map tagged `dead_code: true`. The `ibm-mq` namespace is declared on
`implementation.xml` and `com.ibm.mq.allclient 9.1.1.0` is packaged as a Maven `sharedLibrary`, but
no `ibm-mq` config, publish, listener or consume element exists anywhere. There is no queue, no
producer and no consumer. The shared library nonetheless ships in the deployment artifact and
occupies the runtime classpath.

## Decision

Nothing IBM MQ or JMS related is carried into `sys-billing`: no dependency, no namespace, no
configuration, no listener, no producer. Task `DEP-001` verifies this **mechanically against the
built artifact** rather than by assertion.

This ADR is cross-referenced from ADR-0009 so that the two statements — "there is no delivery
guarantee to reproduce" and "there is therefore no messaging dependency" — are discoverable together.
Separated, each looks like an oversight; together they are a finding.

## Rejected alternatives

**B — carry it forward for safety.** Rejected explicitly, so the question is closed rather than
reopened by someone reading the legacy `pom.xml` during S4. There is nothing it could keep safe: the
map is unambiguous that no messaging behaviour exists anywhere in the application.

## Consequences

+ A substantial third-party client, its transitive dependencies and its CVE surface leave the
  artifact.
+ A future engineer cannot mistake a JMS client on the classpath for evidence of intent.
− If some other part of the estate expects this artifact to bundle the MQ client — a shared base
  image or a deployment convention — its absence surfaces at deploy time. Nothing in the knowledge map
  suggests this, but the deployment model is unknown (R-015). If it turns out to be true, it is a
  platform constraint to be satisfied by the platform, not by this service's POM.

## Verification

- `DEP-001`: the built artifact contains no IBM MQ or JMS client; the service declares no messaging
  listener or producer. Asserted in CI on every build.

## Traces to

`km:node/N-0103` · `spec:capability/—` (deliberately unclaimed) · `risk:R-015` · `adr:ADR-0009`
