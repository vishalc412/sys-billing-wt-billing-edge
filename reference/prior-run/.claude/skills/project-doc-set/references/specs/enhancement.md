> Read `conventions.md` alongside this file. Do not read the other scope specs —
> they describe documents you are not producing.

# Enhancement

Enhancement documents are **deltas**. Restating the existing design wastes the reader's
time and creates a second copy that will drift from the original. Link to the existing
documents and describe only what changes. If no HLD exists to link to, say so and cover
the minimum context needed — do not quietly reconstruct the entire architecture.

## Change Overview

**Reader:** anyone approving or reviewing the change.

1. **What is changing and why** — the requirement or ticket, the business reason, the
   outcome expected.
2. **Scope** — in and out, explicitly.
3. **Impacted components** — a table: component, nature of change (new / modified /
   config / removed), blast radius, owner.
4. **Approach** — the chosen approach in a few paragraphs, plus alternatives considered
   where the choice was close.
5. **Risks** — what could go wrong, likelihood and impact, mitigation.
6. **Dependencies** — other teams, systems, or changes this waits on or blocks.

## HLD Delta

1. **As-is** — only the parts being changed, at container level.
2. **To-be** — the same view after, with the differences called out explicitly. A diagram
   pair is worth more than paragraphs here.
3. **What changes and why** — per architectural change, the reasoning.
4. **NFR impact** — how the change affects each relevant quality attribute. Silence here
   reads as "we did not check".
5. **Interface and contract changes** — anything crossing a boundary, with backward
   compatibility and versioning stated. Breaking changes get their migration path here.
6. **Data changes** — schema, migration strategy, backfill, reversibility.
7. **New or revised decisions** — including any earlier decision this change reverses,
   and why that is now right.

## LLD Delta

Per touched component. Same headings as a full LLD, but populated only where the change
touches — with a before/after framing so a reviewer can see the delta rather than
reconstruct it. Cover explicitly:

- Behaviour that changes, and behaviour deliberately preserved
- New failure modes introduced, and old ones removed
- Configuration added, changed, or retired, with defaults for existing deployments
- Anything that becomes dead code, and whether it is being removed now or later

## Test Approach for a change

1. **What the change requires proving** — the acceptance criteria, in checkable terms.
2. **New tests** — by level, mapped to the acceptance criteria.
3. **Regression scope** — what existing behaviour is at risk and which suites cover it.
   Justify the boundary: "everything" is not a scope, and neither is "the changed files".
4. **Data and environment needs** — anything special this change requires.
5. **Non-functional verification** — performance, security, or migration checks the
   change makes necessary.
6. **Exit criteria** — what must be green to ship.

## Deployment & Rollback

1. **Sequence** — ordered steps including migrations, feature flags, and any required
   ordering between components. Ordering constraints are the thing that goes wrong.
2. **Pre-deployment checks** — what must be true before starting.
3. **Verification** — what to check immediately after, and what "healthy" looks like
   numerically.
4. **Rollback** — the actual procedure, the point of no return (usually a
   non-reversible migration), and what to do past it. "Roll back the deployment" is not a
   rollback plan when the schema has already changed.
5. **Communication** — who needs to know, and when.

## User Guide Delta / Release Notes

1. **What changed for the user** — in their language, benefit first.
2. **New or changed tasks** — updated step-by-step sections.
3. **Anything they must do** — migration steps, re-authentication, settings review.
4. **Deprecations** — what is going away, when, and what replaces it.

---
