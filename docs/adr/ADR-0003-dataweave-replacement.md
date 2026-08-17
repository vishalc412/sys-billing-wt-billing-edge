# ADR-0003: DataWeave is replaced by hand-written mappers and explicit XML templates

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0003-dataweave-replacement.md`

## Context

31 of 103 nodes are transformations, in four distinct classes: SOAP envelope construction (N-0054,
N-0059, N-0064, N-0081), response projection carrying business rules (N-0056, N-0061, N-0066,
N-0077, N-0078, N-0094, N-0100), trivial variable capture (nine nodes) and log-record projection
(five nodes). The hard part is not volume: the *input shape is not stable*, because
`duplicateKeyAsArray` makes a repeated element an array only when it repeats (ADR-0005), so seven
transformations branch on the runtime type of their input.

## Decision

Class-dependent replacement:

| Class | Replacement |
|---|---|
| SOAP envelope construction | An explicit XML template per operation in `adapter/out`, with null-handling stated per adapter (ADR-0006). Not a mapper. |
| Response projection with business rules | Hand-written mappers in `domain`/`application`, one per projection, over the normalised model of ADR-0005. Every recorded edge case becomes a named, tested branch. |
| Trivial variable capture | Plain Java records and method parameters. No mapper, no framework. |
| Log-record projection | One audit-record builder in `billing-edge` (ADR-0017), not five copies. |

Two prohibitions, both load-bearing:

1. **The `mapFields` DuckCreek and BCMS projections (N-0077, N-0078) must not be unified.** They are
   95% identical and the 5% is the defect ADR-0030 decides to preserve. Merging them while that
   defect is preserved is itself a behaviour change.
2. **No MapStruct, and no rules engine.** Adding either at S4 is a dependency-check failure, not a
   style disagreement.

`reqresImpersonateFun.dwl` (N-0052) becomes one tested class implementing one rule (ADR-0036).

## Rejected alternatives

**A — MapStruct everywhere.** Rejected: MapStruct maps between typed beans, and the billing
responses have no schema at all (U8, R-017) — generating the source type *is* the problem, and
MapStruct does not help with it. `default null` versus `default 0`, `hasEscrow` derived from element
presence, and "first term with more than one key" are not expressible without escape hatches at
every interesting field, at which point the tool is adding indirection rather than safety.

**D — a rules engine for the three genuinely complex projections.** Rejected: three rules do not
justify an engine, and it would move the money-rounding rule somewhere no developer will look. The
rules must be readable in Java, in `domain`, without a Spring context.

**B — hand-written mappers for everything including the trivial class.** Rejected as written,
though it is close to the decision: writing a mapper class for a two-field variable capture adds
ceremony that hides where the real rules are. The hybrid puts the effort on the 12 nodes that carry
business rules.

## Consequences

+ Every edge case in the knowledge map becomes an explicit branch that a unit test can name.
+ The duplicate-key hazard is solved once (ADR-0005) instead of in three copies.
+ SOAP construction keeps its byte-level oddities visible in a template rather than buried in a
  framework default.
− More code than a mapping framework would produce, and nothing mechanical stops a developer
  "improving" a rule. The defence is the acceptance criteria and the parity suite, not the tooling.
− Two idioms in one codebase (templates outbound, mappers inbound). The rule for which applies where
  is this ADR, and it will be re-litigated at least once.
− Keeping N-0077 and N-0078 as two near-identical mappers will look like an oversight to every
  reviewer. Each must carry a `@MigratedFrom` note pointing at ADR-0030.

## Verification

- Unit tests per mapper covering each recorded edge case; criteria in ACC-002…006, TXN-001,
  ESC-001, EXT-004, AGY-003, AGY-005.
- Dependency check (CI): `org.mapstruct` and any rules-engine artifact are absent from the BOM and
  from every module POM.
- Annotation check (CI): every mapper class carries `@MigratedFrom` resolving to a real node.

## Traces to

`km:node/N-0036, N-0037, N-0040, N-0042, N-0044, N-0049, N-0051, N-0052, N-0054, N-0056, N-0059,
N-0061, N-0064, N-0066, N-0069, N-0070, N-0072, N-0076, N-0077, N-0078, N-0080, N-0081, N-0083,
N-0087, N-0089, N-0091, N-0093, N-0094, N-0096, N-0099, N-0100` ·
`spec:capability/CAP-005, CAP-006, CAP-007, CAP-009, CAP-010` · `risk:R-017`
