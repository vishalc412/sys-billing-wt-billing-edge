# ADR-0004: Money is BigDecimal with HALF_UP two-decimal normalisation

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0004-money-representation-and-rounding.md`

## Context

The billing systems supply money as four-decimal strings; the API publishes two. The legacy
normalisation is `x as Number as String {format:"0.00"} as Number default null` (N-0077, N-0094) and
its string-terminating variant (N-0061, N-0066). It **rounds, it does not truncate**
(`"2480.8100"` → `2480.81`, `"54.0000"` → `54.00`). The rounding mode is nowhere stated in the
source; the map records DataWeave's default as HALF_UP. DataWeave numbers are arbitrary-precision
decimal, and km §6.4 states plainly that `double` will diverge. Unknown is `null`, not zero.

Three endpoints publish money as JSON numbers, three as JSON strings (ADR-0025, ADR-0012).

## Decision

`BigDecimal` end to end. Normalisation is `setScale(2, RoundingMode.HALF_UP)`, implemented once in
`platform-spi` as `Money.normalise(BigDecimal)` and used by every projection that normalises.

- A value the back end did not supply is `null`, never `ZERO`, and is serialised as JSON `null`.
- A non-numeric value raises, exactly as the legacy coercion raises, and is absorbed by the
  error path of ADR-0010. It is not defaulted.
- Jackson is configured with `WRITE_BIGDECIMAL_AS_PLAIN` and no `double` round trip anywhere in the
  serialisation path, so `54.00` serialises as `54.00` and not `54.0` or `54`.
- The JSON *type* per endpoint is not unified by this ADR. Number-versus-String stays as the legacy
  emits it (ADR-0012, ADR-0025).
- **HALF_UP is recorded here as inherited runtime behaviour, not as a stated business rule.** R-018
  tracks the question to the billing business owner. If the answer is HALF_EVEN, this ADR is
  superseded and every money criterion in ACC-003, TXN-001, AGY-003 and AGY-005 is re-baselined
  rather than parity-tested.

## Rejected alternatives

**B — block until the business owner states the rounding mode.** Rejected: it stops work on six
endpoints for an answer that may take weeks and that nobody may be able to give authoritatively,
while HALF_UP is demonstrably what the running system does today. Shipping observed behaviour and
tracking the question is strictly better than shipping nothing.

**C — `double` or `float`.** Rejected: km §6.4 states it will diverge from the arbitrary-precision
decimal semantics of the source. Recorded so that nobody proposes it as a simplification later.

**Unifying money types across endpoints as part of this decision.** Rejected: Number-versus-String
per endpoint is a published-contract question, not a representation question, and deciding it here
would decide ADR-0012 and ADR-0025 by the back door.

## Consequences

+ Exact decimal semantics, and `null` stays distinguishable from "nothing owed".
+ One implementation of the rule; no per-endpoint drift.
− HALF_UP is an assumption inherited from a runtime default. If it is wrong, every half-cent value
  diverges silently, and the parity suite will not catch it because the baselines were captured from
  the same runtime.
− The serialiser configuration is the kind of detail that is got wrong once and only caught by a
  parity test. It is asserted explicitly rather than assumed (ACC-003-x, TXN-001-x).
− Non-numeric backend values still fail the request, as today. We are preserving a fragility.

## Verification

- Unit: `Money.normalise` over the recorded fixture values, including `"2480.8100"` → `2480.81`,
  `"54.0000"` → `54.00`, absent → `null`.
- Contract: `/primaryAccount` and `/primaryAccount/transactions` emit JSON numbers with two
  decimals including trailing zeros; the worklists and escrow emit strings (ADR-0025).
- Parity: golden replay for ACC-003, TXN-001, AGY-003, AGY-005.

## Traces to

`km:node/N-0061, N-0066, N-0077, N-0078, N-0094, N-0100` ·
`spec:capability/CAP-005, CAP-006, CAP-007, CAP-010` · `risk:R-018`
