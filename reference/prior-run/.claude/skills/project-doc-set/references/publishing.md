# Publishing

Destinations come from `doc-set.config.yaml`, confirmed in the interview. Local always
happens; the rest are additive. **Publish only after the user has seen the documents** —
unwinding a pushed branch or a created page is worse than waiting.

## Local — always

```
docs/
├── README.md            index + traceability
├── 01-hld.md            Markdown source (the source of truth)
├── diagrams/            .drawio
└── dist/                .docx deliverables (+ .build/ intermediates, gitignore these)
```

Whether `.docx` files are committed is a team call — available without a build step, at the
cost of binary churn. Ask once, record it in the config. Say once, at handover, that the
Markdown is the source: a `.docx` edited directly is a fork the next build discards.

## GitHub

For documents that live with the code and should be reviewed like code.

Write into the configured `docs/` path on branch `docs/<scope>-<slug>`. Follow the repo's
existing commit convention — check recent history rather than assuming. Open a PR whose
description summarises coverage and lists the open questions; reviewers engage with a
specific ask and skim "please review docs".

If the change has its own code PR, put the documents in it. Design reviewed alongside the
diff gets read; design reviewed separately does not.

Plain `git` often works when the connector does not — check before falling back to manual
steps.

## Jira and Confluence

Jira holds the work item, Confluence the document.

**Jira** — attach the `.docx`, comment with the Confluence link and a two-line summary.
Update only fields the project actually uses; do not invent workflow transitions.

**Confluence** — one page per document under the configured parent. Mermaid renders via the
Mermaid macro if the space has it, otherwise ship the PNG with the source in an expand
macro; check what the space supports first. Titles must be unique in a space, so prefix
with the system or ticket key (`ORD-412 — HLD Delta: Bulk Import`) rather than a second
page called "HLD".

## Notion

One page per document in the configured database, with properties for scope, system, owner,
status. Notion renders Mermaid in code blocks tagged `mermaid`, so keep the source; attach
the `.docx` as a file property. Its block model mangles deep nesting — flatten to three
heading levels (which the specs already require) and check tables after the first push.

## Linear

For the work item, not the document. Attach the `.docx`, link to where the document
actually lives, and put the summary and open questions in a comment. Long documents inside
Linear issues become unfindable within a month.

## When a connector is not authorized

Do not fail, and do not silently skip.

1. Say which destination was unreachable and that it needs authorizing — claude.ai
   connectors via connector settings, others via MCP configuration.
2. Leave output trivially publishable by hand: built, correctly named, with a
   ready-to-paste summary or comment body where that helps.
3. Offer to publish once it is available, rather than making them ask again.

## The index

Regenerate `docs/README.md` every run: document table with owners, reading order,
traceability from requirement or defect → design → test, and every open question in one
place. Hand-maintained indexes drift within two changes, and an index readers stop trusting
is worse than none.
