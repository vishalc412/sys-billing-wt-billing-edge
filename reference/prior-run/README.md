
# reference/prior-run — NOT PRODUCTION CODE

Everything under this directory is output from a **prior, non-MuleShift migration attempt** on this
repository. It was uncommitted (zero commits existed on `master`) when the MuleShift pipeline ran
stage S3 on 2026-08-14.

**Authoritative decision: [`docs/adr/ADR-0043`](../../docs/adr/ADR-0043-pre-existing-target-repo-reconciliation.md).**

Status of this directory:

- It is **committed** so nothing is lost, and so the two readings of the Mule source can be compared.
- It is **excluded from the Maven reactor** and from CI. Nothing here is built, tested or deployed.
- It is **not in any work packet's `allowed_paths`**. No S4 agent may write here.
- It **may be read** as reference material. It is a second, independent reading of the same Mule
  source and is genuinely useful for the SOAP envelope construction, the SAML request flow and the
  Kustomize manifests.

**It may not be copied without justification.** Several classes here silently correct legacy defects
that this migration has decided to preserve — most importantly
`primaryAccount/mappers/PrimaryAccountResponseMapper.java`, which collapses the DuckCreek and BCMS
branches and applies the DuckCreek "most recent term" rule to BCMS accounts. That is ADR-0028 and
ADR-0030 decided the other way, with no decision record, no consumer check and no test for the case
that matters. Copying it would import both corrections invisibly.

Reuse is permitted only where the work packet's own acceptance criteria and knowledge-map slice
justify the behaviour independently of what is written here.

The repository's root `CLAUDE.md` also belongs to this prior workflow. It has been left untouched on
purpose (an agent should not quietly rewrite the instruction file that governs other agents), but it
is **superseded**: see `MULESHIFT.md` at the repository root.
