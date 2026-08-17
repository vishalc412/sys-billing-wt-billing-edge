# ADR-0034: The `targetValue` on the eight `responseLogFlow` call sites is treated as inert

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0034-defect-responselogflow-targetvalue-inert.md`
Disposition: **preserve — nothing to preserve; confirmation folded into PAR-002**

## Context

N-0022: `responseLogFlow` is invoked with `targetValue="#[requestResponseLog]"` and **no `target`
attribute**, at eight call sites. In Mule 4 `targetValue` is meaningful only with `target`, and
`requestResponseLog` is not a valid unqualified DataWeave binding. The archaeologist recorded it as a
*probable* copy-paste artefact, silently ignored at runtime, and was explicit that confirming
inertness requires a runtime. The system's continued operation strongly implies inertness: a raise
here would fail the whole request through the global handler, which would be extremely visible.

## Decision

Treat as inert. `responseLogFlow`'s successor reads the audit record directly (ADR-0017), and no
equivalent of `targetValue` is carried into the target — there is nothing to carry.

Confirmation is folded into the audit-record baseline capture (task PAR-002, ADR-0021), which has to
happen anyway. If the captured legacy audit record differs from what the map predicts, this is the
first place to look, and the difference would be a **knowledge defect** routed to S1, not an
implementation defect.

## Rejected alternatives

**B — confirm empirically before deciding.** Rejected as a *gate* and adopted as a *step*: blocking
the audit implementation on a runtime observation would stall the edge packet, and the observation is
already required for other reasons, so it costs nothing to be certain slightly later.

**C — reproduce a no-op parameter pass for fidelity.** Rejected explicitly so that no implementer
spends an afternoon on it. There is nothing to preserve; inventing a no-op would be inventing
behaviour.

## Consequences

+ Zero cost, and one fewer piece of Mule idiom carried into Java.
− If the archaeologist's expectation is wrong, the migrated audit record differs in a way no test
  would catch today, because there is no audit-record test at all (R-020). PAR-002 is the only guard.
− The finding is retained for a second reason: eight copy-pasted call sites are corroborating context
  for ADR-0035 (the ordering defect at the eighth site) and ADR-0017 (five duplicated endpoint
  loggers). It is evidence about how this code was written, not just about one attribute.

## Verification

- PAR-002: captured legacy audit record compared field-by-field with the target's.

## Traces to

`km:node/N-0022, N-0048, N-0049, N-0050, N-0051` · `spec:capability/CAP-001, CAP-003` ·
`risk:R-020` · `adr:ADR-0017, ADR-0021, ADR-0035`
