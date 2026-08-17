> Read `conventions.md` alongside this file. Do not read the other scope specs.

# Greenfield

## Project Structure & Working Principles

**Reader:** an engineer joining on day one. **Answers:** where code goes, how we work,
what gets my PR rejected.

1. **What this project is** — the problem, who has it, the shape of the solution. No
   marketing.
2. **Repository layout** — the directories that matter, one line each on what belongs
   there and, where non-obvious, what does not.
3. **Boundaries and dependency rules** — which modules may depend on which, and what
   enforces it (linter, ArchUnit, import checks, review). This is what stops the structure
   eroding, so state the rule *and* its teeth.
4. **Runtime shape** — processes, jobs, workers, schedulers; and how to run it locally,
   precisely enough to follow.
5. **Conventions with teeth** — naming, errors, logging, config, migrations, feature flags.
   Only what is enforced; aspirational style guides are noise.
6. **Working principles** — branching, review expectations, definition of done, how
   decisions get recorded, release cadence. What this team does, not what a textbook says.
7. **Where the documents live** — pointer to the index.

*Dies when* it becomes a generic engineering handbook. Every line should be falsifiable
against this repo.

## HLD — High Level Design

**Reader:** an architect judging whether this is sound and fits the landscape.
**Answers:** what we're building, why this shape, what we traded away.

1. **Context** — business problem, users, system boundary, and what is explicitly out of
   scope. Out-of-scope does real work: it stops the review expanding forever.
2. **Drivers and constraints** — what forces the design: regulatory, existing landscape,
   team shape, cost ceilings, legacy contracts. Constraints you cannot change go here so
   later choices read as reasoned rather than arbitrary.
3. **Platform baseline** — the ground this stands on, from the evidence file: language and
   framework versions actually pinned, build system, database engine and migration tooling,
   runtime and deployment shape. Short, factual, and first — a reader cannot judge any
   later decision without it, and its absence is the most common reason an HLD reads as
   untethered from the system.
4. **Context diagram (C4 L1)** — the system, its users, the systems it talks to.
5. **Architecture overview** — container view (C4 L2) with prose explaining the split. Per
   container: responsibility, technology and version, and why it is separate rather than
   folded into a neighbour.
6. **Key flows** — two to four sequence diagrams for the flows that define the system,
   including at least one failure path. Pick where the design is non-obvious.
7. **Data architecture** — who owns which data, stores and why, entity-level model,
   retention, how data moves between components.
8. **Cross-cutting concerns** — authn/authz, secrets, error handling and idempotency,
   observability, configuration, tenancy. Say how, not that.
9. **Non-functional requirements** — table: attribute, target, how the design meets it, how
   it will be measured. A target with no measurement plan is a wish.
10. **Design decisions** — the ones that could reasonably have gone the other way. Each:
   what was decided, alternatives, why this, what it costs. Three real decisions beat
   fifteen restated defaults.
11. **Risks and open questions** — each with impact and either a mitigation or a named
    owner and a date it must close by.

*Dies when* it describes the architecture without explaining why it is that architecture.
If a competent engineer can't reconstruct your reasoning, the design-decisions
section isn't working.

## LLD — Low Level Design

One per component of real weight. Small components share a document; never one LLD per
class.

**Reader:** whoever implements this, and whoever debugs it at 2am in eighteen months.
**Answers:** how it works inside, what can go wrong, how to change it safely.

1. **Purpose and boundaries** — what it is responsible for, and what it deliberately is not.
2. **Internal structure** — component/class view (C4 L3–L4): modules, dependencies, seams.
3. **Interfaces** — everything crossing the boundary, as tables. APIs: method, path,
   request/response shape, status codes, auth, idempotency, versioning. Messages: topic,
   schema, key, partitioning, ordering, delivery semantics. Libraries: the public surface.
4. **Data** — schema with types, nullability, constraints, indexes and the queries that
   justify them, migration approach. Where transactions begin and end, and why there.
5. **Core logic** — the rules worth explaining, in the order they run: invariants, ordering
   constraints, concurrency, why a surprising branch exists. Do not narrate code that reads
   plainly.
6. **Failure behaviour** — most often missing, most often needed. What fails, how it is
   detected, what happens next: timeouts and their values, retry and backoff, circuit
   breaking, what is idempotent and what is not, partial-failure semantics, poison messages,
   what is logged at what level, what pages someone.
7. **Configuration** — every setting: name, type, default, effect, runtime-changeable?
8. **Performance** — expected volumes, hot paths, known limits, caching with its
   invalidation rule. State the number and where it came from.
9. **Testing notes** — what makes this hard to test and how that is handled: seams, fakes,
   fixtures, the tests that must exist.
10. **Extension points** — where the next change will land, and how to make it without
    unpicking the design.

*Dies when* it paraphrases the code. If a section becomes redundant the moment someone
opens the file, delete it now.

## Test Strategy

**Reader:** test lead and the engineers writing the tests. **Answers:** how we know this
works, and that it still works next quarter.

1. **Scope and quality goals** — what is verified, and what "good enough to ship" means in
   checkable terms.
2. **Risk-based prioritisation** — where defects would hurt most, so where effort
   concentrates. This is what separates a strategy from a wish list.
3. **Test levels** — unit, integration, contract, end-to-end, and what belongs at each. The
   ratio you intend and why; if the pyramid is inverted for a reason, give the reason.
4. **Beyond functional** — performance, resilience and failure injection, security,
   accessibility, data migration, upgrade and rollback. Only those in scope, with triggers.
5. **Environments and data** — what exists, what it contains, how test data is produced and
   refreshed, how personal data is handled.
6. **Automation** — frameworks, where tests live, when they run, what blocks a merge versus
   a release.
7. **Entry and exit criteria** — measurable. "Coverage above X on changed lines, zero open
   criticals, performance within Y% of baseline" — not "testing complete".
8. **Defect management** — severity definitions, triage, what happens to a known issue at
   release.
9. **Traceability** — requirement → test, generated rather than curated.

*Dies when* it is a generic V-model essay. Every section should name this system's actual
risks, tools, and thresholds.

## DevOps & Platform Design

**Only** when infrastructure, pipelines, topology, or observability are in scope.

**Reader:** the platform engineer building it and the operator running it.

1. **Target topology** — environments, regions, networks, what runs where, plus reasoning.
2. **Infrastructure as code** — tooling, repo layout, state handling, how changes promote.
3. **Pipeline** — stages, gates, artefacts and versioning, promotion, deployment strategy
   (rolling, blue/green, canary) and why that one.
4. **Configuration and secrets** — where each lives, how injected, how rotated.
5. **Scaling and capacity** — what scales on what signal, limits, cost shape at load.
6. **Observability** — the metrics, logs and traces that exist; the dashboards someone will
   open; alerts with thresholds and, for each, what the responder does. An alert with no
   runbook is a future ignored alert.
7. **Resilience** — backup and restore with tested recovery times, failover, degraded modes,
   DR against stated RTO/RPO.
8. **Runbook** — start, stop, drain, common failures and first response.

*Dies when* it lists tools instead of describing operations. The test: could someone new
run the system from it?

## User Guide

**Reader:** pick one — end user, API consumer, or operator — and write for them. Two real
audiences means two guides. **Answers:** how do I do the thing I came here to do.

1. **What this is and what it lets you do** — in the reader's vocabulary, not the system's.
2. **Getting started** — the shortest path to first success, complete and literal:
   prerequisites, numbered steps, and what success looks like so they can tell it worked.
3. **Core tasks** — one section per task they actually perform, with steps and expected
   result. Organised by their goals, never by your menu structure or module layout.
4. **Reference** — the exhaustive material (fields, endpoints, parameters), kept separate
   so the task sections stay readable.
5. **Troubleshooting** — real symptoms in their words, with cause and resolution. Include
   the actual error strings; that is what gets searched.
6. **Limits and known issues** — stated plainly. Readers forgive limits and resent
   discovering them.

*Dies when* written from the system's structure outward. The giveaway is a contents list
that mirrors the codebase.

## Index & Traceability

`docs/README.md`. Short, and generated rather than maintained.

1. **The set** — table: document, one-line purpose, owner, link.
2. **Reading order** — for a new joiner, and for a reviewer if they differ.
3. **Traceability** — requirement or ticket → design section → test. This is what an
   assessor asks for first.
4. **Open questions across the set** — all of them, with owners, in one place. Scattered
   open questions are open questions nobody closes.
