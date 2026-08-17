---
description: "Run full Mule-to-Spring migration: pom → config → code → tests → Docker → build"
---

Run a **complete** Mule-to-Spring migration for the sapi-billing project.

Follow **all 6 steps** defined in `.claude/agents/agent_task.md` in order:

1. Consolidate `pom.xml` and document in `sys-billing/report.md`
2. Convert Mule properties → `src/main/resources/application.yml` and Kustomize patches
3. Convert each Mule HTTP flow → `@RestController` + service + mapper (patterns in `.claude/docs/migration/MULE_CONVERSION_GUIDE.md`)
4. Add/convert unit and integration tests into `src/test/java/`
5. Update `Dockerfile` and `docker-compose.yml`
6. Run `mvn -B -DskipTests=false clean test package` → save to `sys-billing/build.log`

Standards to follow:
- `.claude/docs/standards/controller-standards.md`
- `.claude/docs/standards/service-standards.md`
- `.claude/docs/standards/restclient-standard.md`
- `.claude/docs/standards/request-response-standards.md`

On completion return the JSON summary schema from `.claude/agents/agent_task.md`.

