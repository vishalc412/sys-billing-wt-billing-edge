# ADR-0025: PRESERVE — escrow `amountDue` stays a pass-through string

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0025-defect-escrow-amountdue-string.md`
Disposition: **preserve (bug-compatible) in v1**

## Context

N-0100: escrow transactions are passed through unchanged, so `amountDue` is a JSON **string**
(`"958.00"` in the MUnit assertion) while the sibling `/primaryAccount/transactions` coerces to a
**number** (`2480.81`). Across the API, money is split 2–3 between numbers and strings. Because the
escrow values are pass-through, they also carry whatever precision the back end sent rather than the
two-decimal normalisation applied everywhere else (ADR-0004).

## Decision

Preserve. Escrow transaction fields, including `amountDue`, are passed through as strings with no
coercion and no normalisation. The OpenAPI types the field as `string` and carries
`x-legacy-defect: ADR-0025`. Correction to a normalised number is deferred to the v2 frame of
ADR-0012.

## Rejected alternatives

**B — coerce to a two-decimal number, matching the sibling endpoint.** Rejected: `"958.00"` and
`958.00` are different values to a schema validator and to a strict equality check, and no consumer
has been named (R-019). It also introduces a failure mode this endpoint does not have today — a
non-numeric backend value would raise and be absorbed into an error response (ADR-0010), where today
it passes through harmlessly. On a pass-through projection with no backend schema (R-017), that is a
real loss of robustness, not a theoretical one.

**C — keep the string type but normalise it to exactly two decimals.** Rejected: it changes emitted
values (`"958.0"` would become `"958.00"`) while keeping the type inconsistency — the worst of both.

## Consequences

+ Exact parity, including whatever precision the back end happens to send.
− Two transaction endpoints on one API with two money types, published as the official contract.
− The API has no control over its own published precision on this endpoint: a backend precision
  change appears in the response with no code change and no contract change.
− This is the least consequential of the four escrow defects. It is recorded so it does not consume
  the attention that ADR-0024 needs.

## Verification

- Unit + contract: ESC-001 asserts a string-typed `amountDue` with the backend's precision preserved.

## Traces to

`km:node/N-0094, N-0100` · `spec:capability/CAP-006, CAP-007` · `risk:R-017, R-019` ·
`adr:ADR-0004, ADR-0012`
