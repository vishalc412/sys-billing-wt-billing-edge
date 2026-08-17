# ADR-0038: CORRECT — the API console becomes configuration-driven and is disabled in production

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0038-defect-apikit-console-in-production.md`
Disposition: **correct (production exposure removed; the console survives in lower environments)**

## Context

N-0031: the APIkit console is enabled **unconditionally in production**, publishing the internal API
contract to anyone who can reach the listener. The path `/console/*` is hardcoded, not property-
driven. The console flow does not call `setRequestResponse`, so **console traffic is never audited**
(N-0022 ec, N-0050 ec), and it handles only `NOT_FOUND` — every other error falls through to the
opaque shared handler (N-0033 ec). Whether it is reachable from outside the estate depends on
infrastructure outside the repository (U12, R-002), and whether a policy protects it is part of R-003.

## Decision

The console is served by `billing-edge` at a **configurable path**, enabled by configuration
(`billing.console.enabled`), and **disabled in the production profile**. Its 404 body remains the
legacy `{"message": "Resource not found"}` for the environments where it runs, and it remains
unaudited (adding audit records for console traffic is an audit-stream change and belongs to
ADR-0017, not to a security fix).

**Consumers checked:** production console users were not enumerated. The check that was made is
about the nature of the artifact: the console publishes the contract of a customer-billing API to
anyone who can reach the port, unaudited, with an unknown external reachability (R-002) and an
unknown policy posture (R-003). A week of legacy access-log analysis would settle who uses it, and
that request is raised — but it is not a prerequisite, because the cost of being wrong is a developer
losing a bookmark, while the cost of the opposite error is publishing the API contract.

The stated target state remains publishing the contract to the organisation's API catalogue and
removing the console endpoint entirely.

## Rejected alternatives

**A — preserve: console on `/console/*` in every environment, unaudited.** Rejected: it ships an
unaudited, possibly unauthenticated documentation endpoint into production deliberately, at the one
moment when changing it is free.

**C — do not migrate the console at all.** Rejected for cutover: it depends on an organisational API
catalogue existing and being the accepted destination, which is not visible from the knowledge map.
It remains the target state.

**D — enable auditing for console traffic as part of this change.** Rejected: it changes the audit
stream content, which interacts with ADR-0017 and R-021. Smuggling an audit change in under a
security fix is how audit contracts drift.

## Consequences

+ One production exposure removed, at near-zero cost.
+ The console's path and enablement become configuration, which is what they should always have been.
− If someone is using the production console, they lose it, and they find out by using it. The
  access-log analysis is requested but not blocking.
− The audit gap remains: console traffic in lower environments is still unaudited, deliberately.
− A configuration flag that is off in production is a flag someone can turn on in production.
  Enabling it there is a change subject to review, not a runtime toggle.

## Verification

- Integration: with the production profile, any console path returns 404 through the standard
  not-found path; with a lower-environment profile, the console is served and returns the legacy 404
  body for an unknown console path (CON-001).
- Contract: `contracts/billing-edge/openapi.yaml` documents the console as environment-gated.

## Traces to

`km:node/N-0022, N-0031, N-0032, N-0033, N-0050` · `spec:capability/CAP-003, CAP-014` ·
`risk:R-002, R-003, R-021` · `adr:ADR-0013, ADR-0017`
