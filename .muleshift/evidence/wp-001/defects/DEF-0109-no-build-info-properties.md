---
id: DEF-0109
title: "The packaged jar carries no build-info.properties — /info reports only '0'/'--' defaults and no build-time token-substitution check exists (regresses ADR-0018)"
service: billing-edge
severity: medium
class: implementation
raised_at_stage: S5
summary: >
  INF-001-a/c and ADR-0018 require that build-metadata substitution actually runs — /info carries
  the build number, name, git commit and remaining metadata, and the build fails if an
  unsubstituted token reaches the artifact. The spring-boot-maven-plugin declares no build-info
  goal, so META-INF/build-info.properties is never generated, BuildProperties is never
  auto-configured, and /info permanently reports '0' for buildNumber and '--' for textual fields.
  No maven-enforcer or build-time token-substitution check exists, so the legacy's
  unsubstituted @pomBuildNumber@ failure mode (the thing ADR-0018 set out to remove) can recur
  silently.
reproduction: >
  Against the booted container (S5BootedEdgeIT, local profile), GET /info with a valid bearer
  token. The body contains "buildNumber":"0", "buildName":"--", "gitCommit":"--",
  "otherBuildInfo":"--". Inspect the packaged jar target/billing-edge-0.1.0-SNAPSHOT.jar: no entry
  ends with build-info.properties. Read pom.xml: it does not contain "build-info" and does not
  contain "enforcer". The S5 tests assert these observations and name DEF-0109 in their
  @DisplayNames: "the packaged jar carries no build-info.properties, so /info can only report
  defaults" and "nothing fails the BUILD when an unsubstituted token would reach the artifact".
evidence:
  - "test:com.westfield.api.billing.edge.s5.S5ProvenanceAndExemptionProbeTest#thePackagedArtifactCarriesBuildMetadata"
  - "test:com.westfield.api.billing.edge.s5.S5ProvenanceAndExemptionProbeTest#aBuildStepEnforcesTokenSubstitution"
  - "test:com.westfield.api.billing.edge.s5.S5BootedEdgeIT#infoReportsRealBuildProvenance"
  - "evidence:run-s5-billing-edge-r2-e7876edf"
  - "commit:e7876edf4194a32042d19d11a5abdce4447aad3e"
  - "adr:ADR-0018"
traces_to:
  - "km:node/N-0043"
  - "test:com.westfield.api.billing.edge.s5.S5ProvenanceAndExemptionProbeTest#thePackagedArtifactCarriesBuildMetadata"
  - "evidence:run-s5-billing-edge-r2-e7876edf"
status: resolved
blocks_gate: false
triage:
  round: 2
  disposition: >
    Implementation/build defect. The build does not produce build-info.properties and performs no
    build-time token-substitution check, so /info cannot identify the running build and the legacy's
    unsubstituted-Maven-token failure mode (ADR-0018's reason for existing) can recur silently. The
    defaults ('0'/'--') are reachable (INF-001-b holds) but INF-001-a's happy path and INF-001-c's
    build-time guard do not. This is an operational/observability regression (an unidentifiable
    build in production), not a security or data issue. Autonomously fixable by a java-engineer
    re-dispatch (build config). Round 2 of the bounded loop.

    **Resolution (S5 round-2):** Fix verified green in S5 round-2 (run-s5-billing-edge-r2-e7876edf, commit e7876edf4194a32042d19d11a5abdce4447aad3e) by S5ProvenanceAndExemptionProbeTest#thePackagedArtifactCarriesBuildMetadata + #aBuildStepEnforcesTokenSubstitution (surefire) and S5BootedEdgeIT#infoReportsRealBuildProvenance (failsafe, INF-001-a — /info now reports real build provenance, not '0'/'--' defaults). Status advanced open→resolved by migration-architect S6 reconciliation. Not closed: closure is the S7/human gate's call.
  action: >
    Re-dispatch to java-service-engineer (billing-edge): add the spring-boot-maven-plugin
    build-info goal execution to the build (so META-INF/build-info.properties is generated and
    BuildProperties is auto-configured) and add a maven-enforcer (or equivalent) rule that fails
    the build if any unsubstituted @...@ / ${...} Maven token would reach the artifact, per
    ADR-0018. The S5 tests thePackagedArtifactCarriesNoBuildMetadata and
    noBuildStepEnforcesTokenSubstitution must flip from asserting "no build-info / no enforcer" to
    asserting build-info.properties is present and an enforcer rule exists; the wire test
    S5BootedEdgeIT#infoCannotIdentifyTheBuildItIsRunning must flip from asserting '0'/'--' defaults
    to asserting real build provenance.
  decided_by: migration-architect
---

# DEF-0109: no build-info.properties — /info reports only defaults, no build-time token guard

Severity:  medium
Found in:  S5 verify of WP-001, billing-edge
Service:   billing-edge
Criterion: INF-001-a, INF-001-c
Layer:     build / operations

## What is wrong

The S5 probe `@DisplayName`:

> "DEF-0109: the packaged jar carries no build-info.properties, so /info can only report defaults"

and

> "DEF-0109: nothing fails the BUILD when an unsubstituted token would reach the artifact"

The `spring-boot-maven-plugin` declares no `build-info` goal, so `META-INF/build-info.properties` is
never generated, `BuildProperties` is never auto-configured, and every `/info` field falls to its
`'0'`/`'--'` unavailable default — permanently and silently. The test Javadoc notes this is "the
same class of failure as the legacy's unsubstituted `@pomBuildNumber@` that ADR-0018 set out to
remove." No `maven-enforcer` or build-time token-substitution check exists, so INF-001-c ("the build
fails if any such token reaches the artifact") is unsatisfied — the only check that exists
(`StartupValidationRunner`) runs at boot and can never fire because no build metadata is produced
for it to inspect.

## Severity rationale

**Medium.** An unidentifiable build in production is an operational/observability regression
(incident response cannot pin a running instance to a commit). It regresses an accepted ADR
(ADR-0018). No security or data exposure. Not a gate blocker on the S7 HIGH rule.

## Authoritative basis

ADR-0018 (environment configuration parity): "structurally broken configuration fails at startup";
build-metadata substitution must actually run — the legacy ships unsubstituted Maven tokens that
resolve successfully to the token text, and in the target that is a build failure, not a runtime
surprise. INF-001-a (build provenance at /info), INF-001-c (build fails on unsubstituted token).

## Disposition

Class: **implementation** (build configuration). Re-dispatch to the billing-edge java-service-engineer.
Round 2 of the bounded loop. **Status: resolved** — fix verified green in S5 round-2
(run-s5-billing-edge-r2-e7876edf); closure is the S7/human gate's call, not S6's.

## Trace chain

`km:node/N-0043` (/info build provenance) → `pom.xml` (no build-info goal, no enforcer) →
`adapter.in.web.InfoEndpoint` (BuildProperties absent -> '0'/'--' defaults) →
`test:S5ProvenanceAndExemptionProbeTest#thePackagedArtifactCarriesNoBuildMetadata` →
`evidence:run-s5-billing-edge-8f2eec88` · `adr:ADR-0018`