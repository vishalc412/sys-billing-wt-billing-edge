# ADR-0023: PRESERVE — escrow `policyNumber` continues to carry the billing account number

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0023-defect-escrow-policynumber-is-account-number.md`
Disposition: **preserve (bug-compatible) in v1**

## Context

N-0100 (`escrowTransactions-implementation.xml:63`): the output field `policyNumber` is populated
from `billingData.accountNumber` — the 10-digit billing **account** number — while the RAML declares
and exemplifies a 7-character policy number. A caller sending `policyNumber=4444444P` receives
`policyNumber: "3476501380"`. The existing MUnit assertion enshrines the wrong value, so the defect
is protected by its own regression test.

## Decision

Preserve. `/primaryAccount/policy/escrow/transactions` continues to emit the billing account number
in the `policyNumber` field. The generated OpenAPI documents the field with its actual content and
carries `x-legacy-defect: ADR-0023`. The criterion asserting it is named and commented so it reads as
deliberate.

Correction is deferred to the v2 frame of ADR-0012 and is **blocked on ADR-0024 being corrected
first or simultaneously** — which, per ADR-0024, it now is. Even so, correcting the label is deferred
because the label is the join key a consumer may be using, and no consumer has been named (R-019).

## Rejected alternatives

**B — return the caller's `policyNumber`.** Rejected: a consumer joining this response to a billing
account by `policyNumber` would have its join key change from a 10-digit number to a 7-character one,
silently, at cutover. That is the classic shape of a correction that takes down a partner
integration. The ADR rule on corrections requires naming the consumers checked; **none could be
checked** because no inventory exists (R-019). Note the asymmetry with ADR-0024, where the same
absence of an inventory did *not* prevent a correction: there, the risk being carried was a
cross-policy data disclosure; here it is a misleading field name. Different severity, different
answer.

**C — preserve `policyNumber` and add a correctly-named `billingAccountNumber` field.** Rejected: it
is additive and breaks nobody, but it leaves two fields for one concept with one of them permanently
misnamed, and it does not remove the risk that a consumer trusts the name. If a v2 never happens, C
would become the pragmatic fallback.

## Consequences

+ Byte-for-byte parity; no consumer coordination.
− A field whose name is a lie is published in a new system, deliberately, and the OpenAPI now says so
  officially.
− Any consumer that trusts the field name is broken today and stays broken.
− Preservation has an ongoing cost: every future maintainer will read this as a bug and try to fix
  it. The `@MigratedFrom` note and `x-legacy-defect` marker are the only defence.

## Verification

- Unit + contract: ESC-001 asserts `policyNumber` equals the backend account number, not the caller's
  parameter, with the criterion text stating that this is deliberate.

## Traces to

`km:node/N-0100, N-0047, N-0080` · `spec:capability/CAP-007` · `risk:R-019` ·
`adr:ADR-0012, ADR-0024`
