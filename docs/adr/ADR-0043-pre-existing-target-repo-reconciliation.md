# ADR-0043: The pre-existing `sys-billing` work is retained as reference material, not adopted as the target

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: none — this decision arises at S3 from the state of the target repository.

## Context

The target repository was **not empty**. It contained substantial uncommitted work from a prior,
non-MuleShift migration attempt: `git log` shows **zero commits on `master`**, with 41 staged or
untracked paths including a full Spring Boot application under
`src/main/java/com/westfiled/api/billing/` (configs, entities, `pastDueToday`, `primaryAccount`,
services, `soap` packages), six per-environment `application-*.yaml` files, `kustomize/` manifests,
`docs/`, a partially-written `generated/sys/report.md`, its own `CLAUDE.md`, and its own
`.claude/commands/generate-all.md` tooling describing a different workflow.

I read the code before deciding. What it is: a competent, readable Spring Boot 4.1 application with
hand-written mappers, a manual SOAP-over-HTTP client, a Ping SAML client, Resilience4j on outbound
calls, and Kustomize deployment manifests. Its own `generated/sys/report.md` states it was built
from scratch against the Mule source, using the repository's `CLAUDE.md` as an intended architecture
rather than as a description of existing code.

What disqualifies it as the target, concretely, and this is the load-bearing part:

1. **It silently corrects legacy defects that this pipeline requires an ADR to correct.**
   `PrimaryAccountResponseMapper` states in its own Javadoc that the DuckCreek/BCMS branch "is not
   needed" and collapses both branches into one computation that takes the **last** policy term. That
   is ADR-0028 corrected without a decision *and* ADR-0030 corrected by applying the DuckCreek rule to
   BCMS — the specific option ADR-0030 rejects because nothing in the knowledge map supports BCMS term
   ordering. No ADR, no consumer check, no counter, no baseline.
2. **It contradicts accepted decisions.** Resilience4j `@Retry` on outbound calls contradicts ADR-0009
   and ADR-0014 (no retry, and why). Caffeine caching in the request path contradicts ADR-0002.
   Spring Boot 4.1 contradicts ADR-0044.
3. **It has no traceability.** No `@MigratedFrom` anywhere, so no knowledge-map node can be shown to
   be implemented, which is the property the whole pipeline is built to guarantee.
4. **It is not contract-first.** No frozen OpenAPI; the contract is whatever the controllers emit.
5. **It is feature-sliced with no domain isolation.** Business rules sit in `@Component` mappers over
   JAXB entities, so the rules extracted from DataWeave cannot be tested without a Spring context —
   the specific erosion the hexagonal rule exists to prevent.
6. **It is incomplete relative to its own description.** The repository `CLAUDE.md` describes
   `pendingCancellation` and `externalPrimaryAccount` packages, a `schemas/BillingService.wsdl` and a
   generated `com.westfieldgrp.billing` client. None of them exist on disk.

## Decision

**Retain everything; adopt nothing as production code.**

- All prior Java, its `application-*.yaml` files, `generated/`, the prior `.claude/` tooling and the
  prior `pom.xml` move to **`reference/prior-run/`**, which is committed, excluded from the Maven
  reactor, excluded from CI, and marked non-authoritative by `reference/prior-run/README.md`.
- `archive/` (the Mule source copy) and `docs/` are retained in place, unmodified. `kustomize/` is
  retained in place as the starting point for deployment, and is explicitly **not** treated as
  evidence of the deployment model (R-015 stays open).
- MuleShift's structure governs from `scaffold-v1`: `platform/`, `contracts/`, `services/`,
  `docs/adr/`, `.github/workflows/ci.yml`.
- The prior run's code is **permitted as a reference** for S4 engineers — it is a second reading of
  the same Mule source and is genuinely useful for that — but a work packet may only take from it
  what its own knowledge-map slice and contract independently justify. Copying a mapper because it
  exists is not justification.
- The repository's existing root `CLAUDE.md` is **left byte-for-byte untouched**. It describes a
  superseded workflow and is now wrong in several particulars (Spring Boot version, package name,
  packages that do not exist, retry policy). `MULESHIFT.md` is added alongside it as the authoritative
  document, every work packet states which one governs, and **replacing or deleting the stale
  `CLAUDE.md` is raised at the human gate as an action for a human**, because an agent quietly
  rewriting the instruction file that governs other agents is not a decision an agent should take.

## Rejected alternatives

**Adopt the existing package structure and fold the MuleShift artifacts around it.** Rejected on
point 1 alone: the code encodes corrected legacy defects as if they were correct behaviour, with no
disposition record. Adopting it would mean the migration's most consequential preserve/correct
decisions had already been made, invisibly, by a previous run — which is precisely the failure mode
this stage exists to prevent. Points 2–5 are individually fixable; point 1 is a decision made without
a decision record.

**Keep specific classes and retrofit `@MigratedFrom`.** Rejected as a blanket policy, and partially
allowed as a per-class judgement at S4: retrofitting the annotation is easy, but it asserts a
provenance claim the class was not built to satisfy, and the classes most worth keeping (the mappers)
are exactly the ones carrying the undocumented corrections. Per-class reuse is permitted only where
the packet's own criteria justify the behaviour independently.

**Delete the prior work.** Rejected: it is uncommitted, so deletion is irreversible, and it is a
second independent reading of the Mule source — useful cross-check material, especially for the SOAP
envelope and SAML request templates.

**Start a fresh repository and leave `sys-billing` alone.** Rejected: the user's instruction was
explicitly "full pipeline, architect reconciles", and a second repository would leave two candidate
targets with no authority between them.

## Consequences

+ One authoritative structure from `scaffold-v1`, with the prior work preserved and available.
+ Nothing is lost; the reference material can be consulted, cited, or promoted deliberately.
+ Every preserve/correct decision in this migration is now recorded before code exists.
− Real, working code is being set aside. Some of it (SOAP templating, the SAML request flow, the
  Kustomize manifests) will be re-derived at S4 and will end up similar. That is duplicated effort,
  and it is the price of having the decisions recorded.
− `reference/prior-run/` will be mistaken for live code by someone. The README, the reactor exclusion
  and the CI exclusion are the mitigations.
− Leaving a stale `CLAUDE.md` in the repository root is a real hazard: an agent starting a session in
  that repository reads it automatically. `MULESHIFT.md` and the packet instructions mitigate it; a
  human replacing the file removes it. **This is the item most likely to cause an avoidable S4
  mistake, and it is deliberately left for a human.**

## Verification

- `git tag scaffold-v1` contains both `reference/prior-run/**` and the MuleShift scaffold.
- The Maven reactor builds no module under `reference/`; CI path filters exclude it.
- No work packet lists any path under `reference/` in `allowed_paths`.

## Traces to

`km:node/N-0077, N-0078` (the specific defects silently corrected) · `spec:capability/CAP-005` ·
`risk:R-015` · `adr:ADR-0002, ADR-0009, ADR-0014, ADR-0028, ADR-0030, ADR-0044`
