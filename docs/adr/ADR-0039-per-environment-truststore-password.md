# ADR-0039: CORRECT — a new truststore password is generated per environment, and the shared key is not carried forward

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0039-defect-shared-secure-properties-ciphertext.md`
Disposition: **correct (security remediation taken at the cheapest moment)**

## Context

N-0007 and N-0102: all six `secure-*.properties` files contain the **identical ciphertext**
`![...]` for `truststore.password`. Either the same truststore password is used in production as in
local development, or the same encryption key is used across all environments, or both.

Bounding the severity: exactly one value is encrypted at rest, and it protects a **server-certificate
trust store only** — a bundle of public certificates, with no client certificate (N-0014). Everything
genuinely sensitive is fetched at runtime from Thycotic. Nothing in the repository contains a
plaintext credential, and that remains true.

The more consequential possibility is the second one: a **shared encryption key** protects whatever
else is ever encrypted with it.

## Decision

Generate a **new truststore password per environment** during the migration, and store it in the
mechanism chosen by ADR-0008. The legacy `${secure.key}` is **not carried forward** in any form, and
no value from the legacy secure-properties files is re-encrypted and reused.

Removing the password-protected JKS entirely in favour of a platform trust store or a mounted PEM
bundle is the stated target state (it would also make certificate rotation a configuration change
rather than a redeploy — N-0005 ec), and it is adopted once ADR-0008's target state and R-015 are
settled.

## Rejected alternatives

**A — carry the value forward as-is.** Rejected: it migrates a known shared secret into a new system
as a deliberate act, at the one moment when changing it is nearly free, because the truststore is
being rebuilt for a Java runtime anyway.

**B — re-encrypt the same plaintext under a new per-environment key.** Rejected: it fixes the
shared-key problem and leaves the shared password. Half a remediation for the same effort.

**D — remove the JKS now in favour of the platform trust store.** Rejected for cutover: it depends
entirely on the deployment model, which is unknown (R-015). Retained as the target state.

## Consequences

+ Both problems (shared password, shared key) are closed at the cheapest possible moment.
+ Per-environment secret isolation becomes the target's baseline rather than a later remediation.
− A mistake here is a **startup failure per environment** — loud, immediate, and discovered during
  deployment rather than in a test. That is an acceptable failure mode, but it is a deployment-day
  risk that option A does not have.
− Six new values must be generated, stored and owned. That is an operational task with an owner
  outside the migration team, and it is now on the cutover critical path.

## Verification

- CFG-002: the truststore opens with the environment's configured password; no ciphertext from the
  legacy repository appears anywhere in the target.
- SAST/secret scanning: no encrypted or plaintext secret material committed.

## Traces to

`km:node/N-0005, N-0007, N-0014, N-0074, N-0102` · `spec:capability/CAP-012` ·
`risk:R-014, R-015, R-030` · `adr:ADR-0008, ADR-0041`
