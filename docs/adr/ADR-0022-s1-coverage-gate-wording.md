# ADR-0022: The S1 coverage gate is amended to admit configuration-only files

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0022-s1-coverage-gate-wording.md`

## Context

The S1 exit gate reads *"every `config_file` maps to ≥ 1 `flow` node"*. `mule-archaeologist` reported
PASS for 6 of 7 files and stated the exception rather than papering over it: `global.xml` contains no
`<flow>` or `<sub-flow>` element at all — it is configuration-only. All 16 of its elements are
present in the map as N-0001…N-0016, each with a source line, and S2 independently confirmed that all
16 are claimed by a capability. **The gate failed; the map did not.**

## Decision

Amend the gate to: *"every `config_file` maps to ≥ 1 `flow` node, unless the file declares no flow
container, in which case ≥ 1 node of any type"* (the candidate's option C).

The stronger companion check — *every `<flow>`/`<sub-flow>` element maps to exactly one `flow`/
`subflow` node* — is adopted as a **framework follow-up**, not as part of this migration run, because
it is a stage-boundary change requiring a `schema_version` bump and a stated migration path
(per `CLAUDE.md`).

S1's output for this run is treated as having passed its coverage gate. No S1 defect is raised.

## Rejected alternatives

**A — leave the gate as-is and carry a documented standing exception.** Rejected: a gate known to
fail on a correct input is a gate people learn to wave through, and the framework's first invariant
is that no gate accepts a self-assessment as evidence. A standing exception is exactly that, wearing
a different hat.

**B — amend to "≥ 1 node of any type".** Rejected: it loses the property the gate existed for. Under
B, a 400-line flow file recorded as a single config node would pass — which is the actual failure the
gate was built to catch.

**D — adopt the direct flow-element count now.** Rejected for *this run* only, and it is the better
check: counting flow elements and comparing to flow nodes replaces a proxy with the thing it was
proxying for, and it would have passed cleanly here. Changing a stage boundary mid-migration is a
larger risk than the gate's imprecision, so it is a follow-up on the framework rather than on this
migration.

## Consequences

+ The gate becomes decidable and correct for configuration-only files, and stays strict for
  flow-bearing ones.
+ No schema version bump, so no stage boundary moves mid-run.
− The proxy metric survives for now: a flow-bearing file with one recorded node per ten flows would
  still pass. D remains the real fix and is now a framework backlog item with no date.
− The amendment is recorded in an ADR belonging to this migration, while the gate it amends belongs
  to the framework. If the framework text is not updated, the next run inherits the same argument.

## Verification

- The S1 gate definition in `workflows/mule-to-spring.flow.json` reflects the amended wording before
  the next pipeline run. That is the mechanical check for this ADR.

## Traces to

`km:node/N-0001 … N-0016` · `spec:capability/CAP-012`
