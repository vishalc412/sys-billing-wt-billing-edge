# Document conventions

Shared rules for every document, in every scope. Read this plus the one scope file you need — never all three.

The structure of every document, by scope. Each spec names the reader, lists the
sections, and flags the way that particular document usually dies.

Treat the sections as a checklist of what the named reader needs. Delete any that have
nothing real to say — an empty section is worse than a missing one, because it teaches
readers to skim.


## Conventions for all documents

**File naming.** `NN-slug.md`, numbered in reading order: `01-hld.md`,
`02-lld-order-service.md`. The number is reading order, not a version.

**Front matter.** Every document opens with a pandoc YAML block — this drives the Word
title page:

```yaml
---
title: "HLD — Order Platform"
subtitle: "High Level Design · Greenfield"
---
```

**Opening paragraph.** Before any heading, one paragraph stating what this document
covers and who it is for. A reader should be able to tell in ten seconds whether they are
in the right document.

**Heading depth.** Stop at three levels. Deeper nesting means the document is trying to
be two documents.

**Terminology.** Use the codebase's own names for things. Inventing a parallel vocabulary
("the Persistence Subsystem" for `OrderRepository`) forces every reader to maintain a
translation table.

---
