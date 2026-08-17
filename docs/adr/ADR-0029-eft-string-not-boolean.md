# ADR-0029: PRESERVE — `eft` remains the back end's pass-through string

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0029-defect-eft-string-not-boolean.md`
Disposition: **preserve (bug-compatible) in v1**

## Context

N-0077: the RAML declares `eft` as a boolean; the implementation passes the backend value through
and the MUnit payloads assert the **string** `"false"`. In JavaScript the string `"false"` is truthy,
so a consumer writing `if (account.eft)` gets the wrong answer for every account that is *not* on
EFT — the more common case. The value domain is unknown because there is no schema (R-017): if the
back end ever sent `"F"`, `"N"` or `"0"`, that would be published too.

## Decision

Preserve. `eft` is emitted exactly as the back end supplies it, typed as `string` in the generated
OpenAPI with `x-legacy-defect: ADR-0029` and a description stating the truthiness trap explicitly.

Correction is deferred to the v2 frame of ADR-0012, and this ADR records the architect's view that
**`eft` is the strongest single candidate for early correction if any v2 happens at all** — it is the
only defect in the register whose wrong answer is the *default* answer, and it costs half a day to
fix.

## Rejected alternatives

**B — coerce to a JSON boolean.** Rejected for v1 for a reason specific to this defect: the
correction makes things *worse* for adapted consumers while better for compliant ones. A consumer
that learned to write `eft === "true"` would silently start reading every EFT account as non-EFT —
an inversion, not a break. Both populations exist in principle and neither is enumerated (R-019). It
also requires deciding the coercion of unexpected values (`"F"`, `"N"`, `""`, absent) with no value
domain (R-017), and getting that wrong is worse than pass-through.

**C — preserve `eft` and add a boolean companion field.** Rejected: two fields for one flag,
permanently, to avoid one line of coordination.

## Consequences

+ Exact parity; adapted consumers keep working.
− A documented boolean ships as a string in a new system, deliberately, and consumers that trust the
  contract stay wrong in the direction that reports non-EFT accounts as EFT.
− This defect is routinely dismissed as trivial. It is not: it produces a long-lived, low-grade
  wrongness in a UI, which is exactly why it has survived since 2019.

## Verification

- Unit + contract: ACC-004 asserts a string-typed `eft` carrying the backend value verbatim,
  including a non-`true`/`false` value.

## Traces to

`km:node/N-0077, N-0078` · `spec:capability/CAP-005` · `risk:R-017, R-019` · `adr:ADR-0012`
