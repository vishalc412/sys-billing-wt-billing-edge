
# sys-billing — MuleShift working instructions (AUTHORITATIVE)

This repository is the migration target for the MuleSoft application `sapi-billing`. It is produced
and governed by the **MuleShift** pipeline. This file is authoritative for all migration work.

> **The root `CLAUDE.md` in this repository is superseded.** It describes a different, non-MuleShift
> workflow from an earlier attempt and is wrong in several particulars (framework version, base
> package, packages that do not exist on disk, a retry policy this migration has rejected). It has
> been left byte-for-byte untouched on purpose — see ADR-0043 — but where the two disagree, **this
> file and the accepted ADRs govern**. Replacing that file is an action for a human.

## What this repository is

**One deployable artifact composed of three separately owned modules.** It is a modular monolith.
`services/<name>/` here does **not** mean "independently deployable service" — see ADR-0001, which
explains why, and what would have to change for the modules to become services.

```
platform/
  platform-bom/            the ONLY allowed dependency set (ADR-0044)
  platform-spi/            ports the three modules coordinate through - frozen at S3
  platform-observability/  @MigratedFrom, trace-point taxonomy, correlation propagation
  platform-errors/         RFC 9457 record, absorbed-failure and failure-origin abstractions
  platform-testing/        ArchUnit rules, golden-payload/parity harness types
contracts/
  <module>/openapi.yaml    FROZEN at scaffold-v1, read-only at S4
services/
  billing-edge/            CAP-001,002,003,004,011,012,013,014 + the Spring Boot assembly
  billing-account/         CAP-005,006,007,008  (Westfield ESB)
  billing-agency/          CAP-009,010          (WAS BillingService)
docs/adr/                  44 accepted ADRs. Never renumbered, never deleted.
reference/prior-run/       prior non-MuleShift attempt. Reference only. Never built. Never edited.
```

## Rules that are not negotiable

1. **Contracts are read-only.** A mismatch is a contract defect routed to the architect (ADR-0012).
2. **`platform/` is read-only at S4.** Needing a change there is a contract defect, not a commit.
3. **Stay inside your packet's `allowed_paths`.** CI rejects a PR touching anything else. No module
   writes into another module.
4. **Every class and every non-trivial method carries `@MigratedFrom`** resolving to a real
   knowledge-map node. CI checks it. It is what makes "why does this exist?" answerable in five years.
5. **Only what `platform-bom` declares may be used.** A new dependency needs an ADR. Explicitly
   excluded, each for a recorded reason: MapStruct and rules engines (ADR-0003), retry and
   transactions (ADR-0009, ADR-0014), request-path caches (ADR-0002), JMS/IBM MQ (ADR-0040),
   generated SOAP stacks (ADR-0006).
6. **`domain` is framework-free.** The rules extracted from DataWeave must be readable and testable
   without a Spring context. ArchUnit enforces it.
7. **Do not "fix" a preserved defect.** Roughly half the ADRs in `docs/adr/` decide to reproduce
   legacy behaviour that looks wrong and is wrong. Each such site carries a `@MigratedFrom` note and
   an `x-legacy-defect` marker in the contract. If you think one is a bug, you are agreeing with the
   ADR — read the ADR, then leave it alone.
8. **`reference/prior-run/` may be read, not copied.** It silently corrects defects this migration
   preserves (ADR-0043). Reuse needs independent justification from your own criteria.

## Where the decisions live

`docs/adr/`. Start with ADR-0001 (decomposition), ADR-0012 (contract fidelity — the parent of every
preserve/correct decision), ADR-0043 (what happened to the pre-existing code) and ADR-0044 (platform
baseline). The defect dispositions are ADR-0023 to ADR-0042.

Four decisions are explicitly **assumption-based** because the artefact needed to decide them
properly does not exist: ADR-0013 and ADR-0037 (R-003, the API Manager policy set), ADR-0016 (R-013,
the opaque shared error handler) and ADR-0021 (R-025/R-029, parity baselines). Each says so in its
own header. If one of those artefacts arrives, tell the architect: it converts a decision into a
verification.
