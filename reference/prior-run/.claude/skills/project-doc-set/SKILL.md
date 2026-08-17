---
name: project-doc-set
description: "Produces a complete, delivery-grade project document set — HLD, LLDs, test strategy, DevOps/platform design, user guide, and project structure & working principles — for a codebase in any language or stack. Outputs polished Word (.docx) documents with Mermaid and C4 diagrams, plus the Markdown source, and publishes to a local docs tree, GitHub, Jira/Confluence, Notion, or Linear. Runs in three scopes: greenfield project, enhancement to an existing system, or a bug-fix record covering root cause, code changes, and test approach. Use this skill when the user explicitly asks for the document set or its parts — 'create a document set', 'generate the HLD and LLD', 'produce the delivery docs', 'write up the bug fix document', 'document this project end to end' — or names the skill directly. Do NOT use it for a single README, a docstring, an API reference page, a standalone ADR, or meeting notes; those are smaller jobs handled better elsewhere."
compatibility: "Needs pandoc and Graphviz (dot) for the Word build. LibreOffice and pdftoppm enable visual verification. mermaid-cli is used when present; a bundled offline renderer covers the common diagram types otherwise. Publishing to GitHub / Jira / Confluence / Notion / Linear needs those connectors authorized."
---

# Project document set

Produce the documents a delivery ships with: design that survives review, a guide someone
can follow, and a record of why the system is shaped this way.

The failure this skill exists to prevent is the **dead document** — the deliverable that
restates its own headings, hedges every number, and is read once by nobody. Everything
below serves the opposite: documents a new engineer, an auditor, or the on-call at 2am
would be worse off without.

## What gets produced

Scope is the first decision; it sets the shape of everything after.

| Scope | When | Documents |
|---|---|---|
| **greenfield** | New system built from nothing | Project Structure & Working Principles · HLD · one LLD per component · Test Strategy · DevOps & Platform Design¹ · User Guide · Index |
| **enhancement** | A change to something that exists | Change Overview · HLD Delta · LLD Delta per touched component · Test Approach · Deployment & Rollback · Release Notes · Index |
| **bugfix** | A defect being diagnosed and fixed | Defect Analysis & Fix Record (one document) · Index entry |

¹ Only when infrastructure, pipeline, or runtime topology is in scope.

Every scope emits `.md` (source of truth, renders on GitHub/Confluence/Notion) and `.docx`
(the deliverable), built from the same source so they cannot drift. Never inflate a
smaller scope — a bug fix given the full treatment says nobody thought about the reader.

## Workflow

### 1. Scope and config

Confirm the scope, then read `doc-set.config.yaml` from the project root or `docs/`. It
holds org name, output paths, publishing targets, and persona defaults so the user
configures once instead of re-answering every run.

Missing? Copy `config/doc-set.config.example.yaml` in, fill it from the interview, mention
it once. If the project is read-only, write it beside the output instead.

### 2. Gather evidence — mandatory, before anything is drafted

Reading source files is the most expensive thing this skill does, and most of what gets
read is never quoted. Work outside-in: cheap written context first, source only where it
runs out.

Run this first. One command, nothing to decide — it finds a `.crib/` code graph and
queries it, or scans the repo if there is none, and always writes the evidence file:

```bash
python scripts/evidence.py <repo> --out-dir docs/<slug>/.evidence [--feature FLL]
```

Read `.evidence/evidence.md` before writing a word. It opens with the **platform
baseline** — build system, frameworks and versions, database, migrations, deployment —
then the feature slice: components, HTTP surface, call chains, data model, failure
handling, each with `file:line`. `.evidence/diagrams.md` holds Mermaid generated from the
graph; prune it to what is load-bearing rather than pasting it whole.

**Do not draft without this file.** A document written from filenames describes a system
nobody built, and the quality gate in phase 6 will reject it. The `--feature` slice alone
is never enough for an HLD: it deliberately excludes the platform, which is how a design
document ends up never naming the framework, database or build system underneath it.

`.crib/INDEX.md` (a markdown crib, shared with the `cartographer` skill) may also exist
alongside the graph. Read the index first, zoom into topic files only as questions demand,
and never bulk-read it — it carries *why*, where the graph carries *what*.

The crib is a **prior, not an authority.** It seeds drafts and prunes questions; it never
overrules code or user. Mark a question `resolved from crib` only if the crib answers it
*directly* and the topic is under the index's **Authoritative on** list. On conflict: code
wins on *what is*, the user wins on *what should be*, and you surface it rather than
quietly picking. Never write to `.crib/` — propose additions inline:

```
📎 Crib proposal → .crib/domain.md
+ Settlement runs T+1, not intraday. (from Q7)
Accept / reject / edit?
```

**The scan.** For the rest, let the script read the repo — it returns entry points, data
model, integrations, config, guardrails and tests with `path:line`, for a fraction of what
opening the files costs:

```bash
python scripts/scan_repo.py <repo>                    # unfamiliar repo
python scripts/scan_repo.py <repo> --since HEAD~5     # enhancement or bugfix
python scripts/scan_repo.py <repo> --scope app/orders # known blast radius
```

Then open in full only the handful of files the digest makes you curious about — usually
three or four, not the repository. Scope by blast radius, never by repo size.

Read the **Guardrails** section for what is *absent* as much as what is there. An
idempotency key with an index but no unique constraint, a retry with no backoff, a
transaction that stops short of the write it protects — absence is where defects live, and
it is invisible if you only read what exists.

For enhancement and bugfix, also read the diff and the history around the touched files. A
fix whose author never asked why the code was written that way becomes next quarter's
incident.

What you still cannot determine becomes the interview — and only that.

### 3. Interview

Scaled to scope: full for greenfield, focused for enhancement, tight for a bug fix. See
`references/interview.md` for the question banks.

Two rules outrank the list. Ask only what code and crib cannot answer — intent,
constraints, targets, ownership, what was rejected. And ask in batches with your
recommendation attached, so the user confirms a considered position instead of filling in
a form. Confirm publishing destinations here too.

If the interview can't happen — non-interactive run, or the user says go ahead — do not
invent the answers. Mark each assumption where it is used ("assumed 99.9% availability;
not confirmed") and carry every one into open questions. A visible assumption is useful; a
silent one is a defect that propagates.

### 4. Draft

Read `references/specs/conventions.md` plus **one** scope file — `specs/greenfield.md`,
`specs/enhancement.md`, or `specs/bugfix.md`. They are split so a bug fix never pays for
the greenfield spec.

Treat the structure as a checklist of what the reader needs, not a form. A section with
nothing to say gets deleted, not filled with "N/A".

**Cover the surface.** Every entry point in the evidence — each HTTP route, consumer, and
scheduled job — belongs in exactly one document, and every component the evidence names
must appear somewhere. Documenting the interesting half is the most common way a set feels
thin: the reader hits the route you skipped and stops trusting the rest. Where the surface
is large, use tables for the routine members and prose for the ones with real behaviour.
The gate checks this, and will tell you the fraction you covered.

**Diagrams are judged on whether a path can be traced, not on whether they render.** Keep
each under about twelve nodes and split by concern rather than cramming; every box needs at
least one edge; label containers with their technology and version (`[Javalin 7.1.0]`); and
name real stores rather than drawing one box called "Relational DB". The gate checks these
too.

Write one document fully to disk before starting the next. Holding four drafts in memory
to keep them consistent costs a lot and buys little — consistency comes from the shared
digest and the specs.

### 5. Review board

Re-read each draft as each reviewer and fix what they would send back; see
`references/personas.md`. This catches what first drafts reliably miss, because drafting
attention goes to what you know rather than what is absent.

Enterprise Architect, Principal Engineer (for the actual stack), and Test Architect always.
DevOps & Platform Architect only when infrastructure, pipelines, topology, or observability
change — and only then does that document exist. Security is a checklist on every document,
because a security section in one document is one every other document skips.

### 6. Build

```bash
python scripts/docset.py build docs/*.md --out-dir docs/dist --verify
python scripts/make_c4_drawio.py c4-model.json -o docs/diagrams/architecture.drawio
```

`--verify` rasterises the first pages. **Look at them.** Misplaced figures, collapsed
tables, and illegible diagrams are invisible in Markdown and obvious on the page; a
document you have not looked at is one you are guessing about.

### 7. Publish and index

Publish to the destinations confirmed in phase 3 — `references/publishing.md` covers each
one and what to do when a connector is not authorized. Publish only after the user has
seen the documents; unwinding a pushed branch or a created page is worse than waiting.

Regenerate the index (`docs/README.md`) rather than editing it: document table with owners,
reading order, traceability from requirement or defect → design → test, and every open
question across the set in one place. Hand-maintained indexes drift within two changes.

## The Live Document Test

Apply while writing, not as a cleanup pass.

- **Cut any sentence the reader could have written without reading you.** "The system must
  be scalable" is furniture. "Order writes are single-region; a region loss costs up to 40
  seconds of accepted orders" is information.
- **Anchor every claim** to a file path, config key, version, measured number, or a named
  person's answer. One unanchored claim makes a reader doubt the rest.
- **State unknowns as questions with an owner.** "TBD" is litter; hedging is worse because
  it looks like content.
- **No ceremony** — no sign-off tables, revision grids, distribution lists, or invented
  dates. Empty ceremony announces a document written for a process, not a reader.
- **Reference every figure in the prose**, and make it say what the prose does not.
- **Tables for enumerable facts, prose for reasoning.** Reasoning in bullet fragments
  loses the logic that made it worth writing.
- **One named reader per document.** The HLD is for an architect judging fit; the LLD for
  whoever debugs it at 2am; the user guide for someone with a job to finish and no interest
  in your architecture. Mixing them serves none of them.

## Diagrams

Mermaid lives inline in the Markdown — it renders on GitHub, Confluence and Notion and
diffs in review — and the build renders it to images for Word. The first line **inside** each fence must be
`%% caption: Figure N — What it shows`. Placed above the fence instead, it prints as
literal `%% caption:` junk in the Word file.

| Need | Use |
|---|---|
| C4 L1 context, L2 container | `.drawio` via `make_c4_drawio.py`, plus inline Mermaid for readability |
| C4 L3 component | `.drawio` if maintained, Mermaid if illustrative |
| Request / message flows | Mermaid `sequenceDiagram` |
| Data model | Mermaid `erDiagram` |
| Lifecycle, status transitions | Mermaid `stateDiagram-v2` |
| Deployment topology, pipelines | Mermaid `flowchart` with `subgraph` per zone |

The offline renderer covers `flowchart`/`graph`, `sequenceDiagram`, `erDiagram`,
`classDiagram`, `stateDiagram-v2`. Anything else needs mermaid-cli; without it the build
keeps the source as a code block and warns rather than shipping a gap. Pre-flight a large
build with `python scripts/docset.py render docs/*.md --check`.

Draw what is load-bearing. Three diagrams carrying the design beat twelve restating the
folder structure.

## Bundled files

| Path | Read when |
|---|---|
| `references/interview.md` | Phase 3 — question banks per scope |
| `references/specs/conventions.md` + one scope file | Phase 4 — document structure |
| `references/personas.md` | Phase 5 — the review lenses |
| `references/publishing.md` | Phase 7 — destinations and fallbacks |
| `config/doc-set.config.example.yaml` | Phase 1 — configuration template |
| `scripts/evidence.py` | Phase 2 — **run first**; crib graph or repo scan, always writes evidence |
| `scripts/docset.py` | Phase 6 — `build` for Word, `render` for diagrams |
| `scripts/make_c4_drawio.py` | Editable C4 `.drawio` from a small JSON model |

To re-theme the Word output, open `assets/reference.docx` in Word and restyle it — pandoc
uses whatever it finds there.
