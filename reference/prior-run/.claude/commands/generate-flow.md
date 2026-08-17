---
description: Convert a single Mule flow to a Spring RestController, service, mapper, and tests
argument-hint: "<flow-basename>  e.g.  getInvoiceFlow"
---

Convert the Mule flow named **$ARGUMENTS** to Spring Boot.

Steps (scoped to this flow only):

1. Locate the flow file in `archive/sapi-billing/src/main/mule/` matching `$ARGUMENTS`.
2. Identify the HTTP listener path, method, request type, and response type.
3. Generate `@RestController` following `.claude/docs/standards/controller-standards.md`.
4. Generate the service class following `.claude/docs/standards/service-standards.md`.
5. Generate mapper class if DataWeave transformation exists (see `.claude/docs/migration/MULE_CONVERSION_GUIDE.md` § 3).
6. Add properties to `src/main/resources/application.yml` if needed.
7. Write unit tests in `src/test/java/` (patterns in `.claude/docs/testing/TESTING_STRATEGY.md`).
8. Append a summary entry to `sys-billing/report.md`.

If the flow cannot be safely converted, create `sys-billing/manual/$ARGUMENTS.todo.md`.

Return the JSON summary from `.claude/agents/agent_task.md` scoped to this flow.

