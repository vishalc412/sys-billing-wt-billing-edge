# ADR-0005: Normalise the backend XML on input; preserve the legacy output shape exactly

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0005-duplicate-key-normalisation.md`

## Context

The ESB returns XML whose repeated sibling elements become duplicate keys; three byte-identical
transformations normalise it with `duplicateKeyAsArray=true` (N-0076, N-0093, N-0099). That flag
produces an array **only when the element repeats**, which is the root cause of every `is Object` /
`is Array` / `typeOf` branch in the application and of these concrete outcomes: `policies` emitted
as `null` rather than `[]` when absent (N-0077 ec); `sizeOf(null) - 1` producing index `-1`
undefended (N-0077 ec); an array-valued `billingStatus` on the BCMS branch (N-0078, ADR-0030);
escrow terms mixed across policies on a multi-policy account (N-0100 ec); and a *substring* type test
`typeOf(x) contains "Object"` (N-0094 ec).

The response shape of this API therefore depends on the data, not on a schema.

## Decision

Parse each backend response **once**, in the owning module's `adapter/out`, into a normalised model
in which a repeated element is **always** a `List` and a single occurrence is a one-element `List`.
All type-branching disappears from the mappers and becomes one decision in the parser.

The **output** shape is then reproduced exactly as the legacy emits it, including the ugly cases:
`policies` is emitted as `null` where the legacy emits `null`; `billingStatus` is emitted as an
array where ADR-0030 preserves that; a bare `[]` is emitted where the legacy emits a bare `[]`
(ADR-0026). Each such emission is an explicit, named, tested criterion (ACC-005-e, ACC-006-c,
ESC-001-f) carrying a `@MigratedFrom` note, so it reads as deliberate rather than as a bug awaiting
a fix.

## Rejected alternatives

**B — normalise the input and stabilise the output too** (always `[]`, always a string
`billingStatus`). Rejected: it changes what consumers receive, and nobody has enumerated the
consumers (R-019). A consumer testing `policies == null` behaves differently from one testing
`policies.isEmpty()`. It would also decide ADR-0030 and ADR-0012 by the back door. This is the
option to revisit first if a consumer inventory ever arrives.

**C — port the branching literally, including `typeOf(x) contains "Object"`.** Rejected: a substring
match on a type name has no natural Java equivalent, so it would be reimplemented as something
subtly different anyway. The fidelity is illusory and the cost is real.

## Consequences

+ The genuine hazard — three copies of a fragile parse and seven data-dependent branches — is gone,
  with not one published response byte changed.
+ Mappers become unit-testable without constructing pathological XML for every case.
− The ugly output cases remain publishable, and the target's OpenAPI cannot fully type them
  (`policies` is `nullable`, `billingStatus` is `oneOf`).
− Each projection needs an explicit "emit legacy-shaped output" step that looks like a bug. Reviewers
  will challenge it; the `@MigratedFrom` note and the criterion id are the answer.

## Verification

- Unit: parser tests for zero, one and many occurrences of `policies`, `policyTerm`, `transactions`,
  `policyNumbers`.
- Unit: projection tests asserting `policies: null` for the absent case (ACC-005-e) and array-valued
  `billingStatus` for multi-term BCMS (ACC-006-c).
- Parity: golden replay where a baseline exists (ADR-0021).

## Traces to

`km:node/N-0076, N-0077, N-0078, N-0093, N-0094, N-0099, N-0100, N-0046, N-0047` ·
`spec:capability/CAP-005, CAP-006, CAP-007` · `risk:R-019`
