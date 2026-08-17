# ADR-0044: Target platform baseline — Java 21, Spring Boot 3.5, `com.westfield.api.billing`

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: none — this decision arises at S3 from the conflict between the pre-existing repository and
the MuleShift scaffold.

## Context

Three conflicts had to be settled before a single POM could be written.

1. **Framework version.** The pre-existing `pom.xml` uses `spring-boot-starter-parent:4.1.0`. The
   MuleShift scaffold specifies Spring Boot 3.x. The prior run's own report records that Spring Boot
   4.1 dropped an artifact it expected (`spring-boot-starter-aop`) and that it pinned three
   dependencies outside the BOM to compensate.
2. **Package and coordinates.** The code on disk uses `com.westfiled.api.billing` and groupId
   `com.westfiled.api` — a typo. The repository's own `CLAUDE.md` states the intent as
   `com.westfield.api.billing` / `com.westfield.api`, and the prior run explicitly declined to fix it
   as "out of scope".
3. **Layout.** The repository `CLAUDE.md` mandates feature-slicing with no domain isolation; the
   MuleShift scaffold mandates hexagonal layering enforced by ArchUnit.

## Decision

| Item | Value |
|---|---|
| Java | 21 (`maven.compiler.release=21`) |
| Spring Boot | **3.5.x**, pinned in `platform-bom` |
| groupId | `com.westfield.api` |
| artifactId | `sys-billing` (aggregator), per-module artifacts `platform-*`, `billing-*` |
| Base package | `com.westfield.api.billing` |
| Module packages | `com.westfield.api.billing.<module>.{domain,application,adapter.in,adapter.out}` |
| Layout | Hexagonal per module (ADR-0001), enforced by ArchUnit |
| Dependency set | Only what `platform-bom` declares; anything else needs an ADR |

The typo is fixed **now**, at the only moment it is free: there are zero commits on `master`, so no
history is rewritten and no branch is invalidated.

## Rejected alternatives

**Spring Boot 4.1, as the pre-existing POM has it.** Rejected. This migration's risk budget should be
spent on billing semantics — money rounding, fault classification, entitlement — not on being early
adopters of Spring Framework 7. The prior run already hit ecosystem lag (a missing starter) within a
single session, and the S5 machinery this pipeline depends on (Testcontainers, WireMock, contract
verification, ArchUnit) has the most mileage on Boot 3.5. If organisational policy requires Boot 4,
that is a platform decision that should be taken deliberately with its own upgrade plan, not
inherited from a scaffold that happened to be generated with it.

**Spring Boot 3.2/3.3.** Rejected: closer to the end of the OSS support window, for no benefit.

**Keeping `com.westfiled`.** Rejected: a typo in every import of a system that will live for years,
retained to avoid a rename that costs nothing today because nothing is committed. The prior run's
"out of scope" was reasonable for its scope; it is not reasonable for a scaffold that everything else
is built on.

**Feature-slicing per the repository `CLAUDE.md`.** Rejected: it puts business rules in Spring
components over JAXB entities, so the rules extracted from DataWeave — the part of this migration that
must be readable and testable without a framework — cannot be tested without a Spring context.
Feature-slicing survives at the *module* level (ADR-0001); hexagonal layering governs inside each
module.

## Consequences

+ One dependency set, one layout, one package root, all mechanically enforced.
+ The typo never reaches a released artifact.
− Every reference class in `reference/prior-run/` uses the old package name, so any code consulted
  from it needs its imports adjusted. Minor, but constant.
− Choosing 3.5 over 4.1 will need revisiting within the service's lifetime, and the upgrade is now a
  future project rather than a present one. That is a deliberate deferral, not an oversight.
− If the organisation has already standardised on Boot 4 elsewhere, this service is out of step and
  that must be raised at the gate.

## Verification

- CI dependency check: every module's dependencies resolve through `platform-bom`; any dependency not
  in the BOM fails the build.
- CI: no package named `com.westfiled` exists outside `reference/`.
- ArchUnit: layering rules pass for all three modules.

## Traces to

`spec:capability/—` (platform decision) · `adr:ADR-0001, ADR-0003, ADR-0043`
