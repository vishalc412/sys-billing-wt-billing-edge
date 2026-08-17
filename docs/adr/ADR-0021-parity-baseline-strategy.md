# ADR-0021: Parity baselines come from a legacy instance where possible, synthetic fixtures elsewhere, and synthetic baselines are labelled as such

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0021-parity-baseline-strategy.md`
Assumption-based: **partly — depends on R-025 (no known runnable legacy instance) and R-029 (no PII approval path).**

## Context

Only four usable golden payloads exist, covering three endpoints and only the DuckCreek branch
(km §10). There is **no coverage at all** for: the main funnel, the console, all seven APIkit error
mappings, `/info`, the audit record, the masking rule, both agency worklists, both external-account
routes, `GetSAMLToken`, `error_Flow`, and the BCMS branch of `mapFields`. Four of eight endpoints
have zero coverage (R-020).

One existing test is **vacuous**: its mock supplies an already-mapped payload, so the mapper never
runs and the assertion compares the mock to itself. Two fixture sets (`_0Trans`, `_1Trans`) are
**stale** and document a previous contract (R-027).

Two constraints on capture: there may be **no runnable legacy environment** (R-025 — and `local`
cannot start three endpoints), and there is **no approved path for the PII** in captured payloads
(R-029).

## Decision

Baselines are produced in this order, and each baseline records **which mechanism produced it**:

1. **Captured from a legacy instance** (`dev` or `test`, never `local`) against recorded backend
   responses. Started immediately, because it also closes R-009 (204 bodies), R-005 (do the opaque
   log sub-flows mutate the payload?) and R-024 (stale status) for free.
2. **Synthetic fixtures** derived from the knowledge map's 278 edge cases, filling the cases capture
   cannot reach.
3. **Production shape census** — read-only sampling of response *shapes*, not payloads — only if and
   when R-029 produces a data-handling approval.

Three rules that make this honest:

- **No endpoint ships without a baseline.** "We tested it by hand" is not a baseline.
- **Every synthetic baseline is labelled `origin: synthetic`** in the golden-payload metadata, and
  the S5 evidence pack reports the synthetic count per endpoint. A parity suite that is green on
  synthetic-only baselines for an endpoint is reported as *unverified parity*, not as parity. This
  is the mechanism that stops R-025 degrading into a green board over a broken system.
- **PII handling.** Capture happens only in non-production environments; payloads are stored with
  structure-preserving pseudonymisation (fixed-length token substitution that preserves length,
  character class, repetition and duplication), implemented once in `platform-testing`, so the shape
  irregularities being captured survive the scrubbing. No production payload capture until R-029 has
  a named data-protection owner.

Two prohibitions: the vacuous MUnit test is **not** parity evidence and is not ported; the
`_0Trans`/`_1Trans` fixtures are **stale** and must not be used to derive any expectation.

## Rejected alternatives

**A — capture live production traffic as the primary mechanism.** Rejected as the primary route:
real shapes and real distribution are exactly what we want, but there is no data-handling approval,
no storage location and no scrubbing standard (R-029), and doing it anyway would create a compliance
incident out of a migration.

**B — synthetic fixtures only.** Rejected as a *strategy*, accepted as a *component*: synthetic
fixtures encode our understanding rather than the backend's behaviour, so a misunderstanding makes
fixture and implementation wrong in the same direction and the parity test passes. That is the single
most likely way this migration produces a green board and a broken system (R-025). Hence the labelling
rule.

**C — legacy capture only.** Rejected: it cannot reach the recorded edge cases that require
pathological backend responses, and it depends on an environment that may not exist.

## Consequences

+ Real legacy behaviour where it can be had; explicit, visible uncertainty where it cannot.
+ Four unknowns (R-005, R-009, R-024, and the audit record) are closed as a by-product of step 1.
− If R-025 resolves to "no legacy instance", a large share of the suite is synthetic and the S5
  evidence pack will say so loudly. That is the correct outcome, and it will be uncomfortable.
− Pseudonymisation is itself a risk: get the token substitution wrong and a shape irregularity is
  destroyed, which is the failure mode this scheme is designed to avoid.
− Three mechanisms need one owner for composition. Today that owner is the test-evidence engineer at
  S5; the *capture access* has no owner and is the schedule risk.

## Verification

- PAR-001 and PAR-002 criteria.
- Mechanical: every `golden_payload_ref` in every task resolves to a file carrying `origin` metadata;
  the S5 evidence pack aggregates synthetic counts per endpoint.
- The vacuous test and the stale fixtures appear in no test resource in the target repository.

## Traces to

`km:node/N-0034, N-0038, N-0039, N-0041, N-0043, N-0048, N-0050, N-0052, N-0053, N-0058, N-0063,
N-0068, N-0071, N-0078, N-0085, N-0088, N-0095` ·
`spec:capability/CAP-001, CAP-003, CAP-005, CAP-009, CAP-010, CAP-011, CAP-013, CAP-014` ·
`risk:R-020, R-025, R-027, R-029`
