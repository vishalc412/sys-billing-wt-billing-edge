---
id: DEF-0100
title: "The Spring application context does not start — @Component adapters declare two constructors with no @Autowired"
service: billing-edge
severity: high
class: implementation
raised_at_stage: S5
summary: >
  The deployable does not boot. ThycoticCredentialProvider and WsTrustSecurityTokenService are each
  @Component with two constructors (a public production one and a package-private test seam taking
  an HttpClient) and neither constructor is marked @Autowired. Spring's implicit-constructor
  injection only applies when exactly one constructor exists; with two and no @Autowired marker it
  falls back to a no-argument constructor that does not exist, so context startup fails on
  thycoticCredentialProvider ("No default constructor found"). No test in the S4 suite boots a
  Spring context, so the assembly was never exercised and the build was green anyway.
reproduction: >
  Run `new SpringApplicationBuilder(SysBillingApplication.class).web(WebApplicationType.NONE)
  .profiles("local").run()`. Startup throws with a message containing
  "thycoticCredentialProvider" and a stack trace containing "No default constructor found".
  Structurally, both ThycoticCredentialProvider and WsTrustSecurityTokenService declare two
  constructors and neither is annotated @Autowired. The S5 integration suite boots only by
  including S5TestSupport with spring.main.allow-bean-definition-overriding=true, which is the
  documented DEF-0100 work-around and is not present in the production artifact.
evidence:
  - "test:com.westfield.api.billing.edge.s5.S5ContextStartupTest#theApplicationContextFailsToStart"
  - "test:com.westfield.api.billing.edge.s5.S5ContextStartupTest#theSameFaultIsPresentOnTheStsAdapter"
  - "evidence:run-s5-billing-edge-8f2eec88"
  - "commit:8f2eec88b614f310a455f6e17b6c09d80f1205f4"
traces_to:
  - "mule:flow/sapi-billing-search-main"
  - "test:com.westfield.api.billing.edge.s5.S5ContextStartupTest#theApplicationContextFailsToStart"
  - "evidence:run-s5-billing-edge-8f2eec88"
status: open
blocks_gate: true
triage:
  round: 2
  disposition: >
    Implementation defect. The service cannot start in any profile without the S5TestSupport
    work-around, so the production deployable is unbootable. The fix is a single bean-definition
    correction: mark the production constructor @Autowired on both ThycoticCredentialProvider and
    WsTrustSecurityTokenService (or reduce each to one constructor and inject the HttpClient via
    a factory/test-config). No ADR or contract is implicated; this is pure assembly wiring that the
    S4 suite's no-context test strategy allowed through. Autonomously fixable by a java-engineer
    re-dispatch. Note: this is Round 2 of a bounded-three-round loop for the WP-001 edge defect set.
  action: >
    Re-dispatch to java-service-engineer (billing-edge): annotate the production constructor of
    ThycoticCredentialProvider and WsTrustSecurityTokenService with @Autowired (or collapse to a
    single constructor and move the HttpClient test seam behind a test configuration). The S5
    test S5ContextStartupTest#theApplicationContextFailsToStart must flip from asserting the
    startup failure to asserting a successful context boot; the S5TestSupport DEF-0100 work-around
    (bean-definition-overriding) must be removed once the context boots unaided.
  decided_by: migration-architect
---

# DEF-0100: the Spring context does not start

Severity:  high
Found in:  S5 verify of WP-001, billing-edge
Service:   billing-edge
Layer:     assembly / bean wiring

## What is wrong

No test in the S4 suite boots a Spring application context — every one of the 206 tests the S4
build report counts is a plain JUnit test over a hand-constructed object or a hand-assembled filter
chain. The assembly itself (which beans exist, whether they can be constructed, whether the filters
are registered) was never executed, and the build was green anyway.

The S5 context-startup probe boots the real `SysBillingApplication` with the `local` profile and
records what happens. It fails. Root cause (quoted from the S5 test's own Javadoc):

> `ThycoticCredentialProvider` and `WsTrustSecurityTokenService` are each annotated `@Component` and
> each declare TWO constructors — a public production one and a package-private test seam taking an
> injected `HttpClient`. Spring's implicit constructor injection only applies when a component
> declares exactly one constructor; with two and no `@Autowired` marker it falls back to the
> no-argument constructor, which does not exist.

The `@DisplayName` on the failing test is:

> "booting SysBillingApplication with the local profile fails on thycoticCredentialProvider"

asserting `hasMessageContaining("thycoticCredentialProvider")` and
`hasStackTraceContaining("No default constructor found")`. A second test asserts structurally that
both adapter classes declare two constructors and neither is `@Autowired`.

## Why this is HIGH

A deployable that cannot start a context cannot run in any environment. The S5 integration suite
(S5BootedEdgeIT, S5ProdProfileIT) only boots by loading `S5TestSupport` with
`spring.main.allow-bean-definition-overriding=true` — a work-around labelled in `S5TestSupport` as
the "DEF-0100 work-around, present only so that the rest of the assembly can be verified." That
work-around is not present in the production artifact. Cutover is impossible until this is fixed.

## Authoritative basis

No ADR sanctions a non-booting assembly; this is an implementation/wiring defect, not a
specification or contract question. The two-constructor pattern was a test-seam choice that broke
Spring's implicit constructor resolution.

## Disposition

Class: **implementation**. Re-dispatch to the billing-edge java-service-engineer. Round 2 of the
bounded loop for this defect set. Status remains **open** — S6 triages and dispositions; it does
not close.

## Trace chain

`mule:flow/sapi-billing-search-main` (the main flow the boot must support) →
`adapter.out.vault.ThycoticCredentialProvider` / `adapter.out.sts.WsTrustSecurityTokenService` →
`test:S5ContextStartupTest#theApplicationContextFailsToStart` →
`evidence:run-s5-billing-edge-8f2eec88`

## Related

DEF-0105 (the same two adapters' HttpClient is built with no SSLContext) — the same two classes,
a different consequence. Fixing DEF-0100 unblocks the context; DEF-0105 is the runtime behaviour
of the HttpClient those adapters build.