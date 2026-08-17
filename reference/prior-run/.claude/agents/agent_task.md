Automated agent instructions (detailed behavior when receiving a single-line command)

Purpose
This file tells an automated LLM-based agent exactly how to respond when the user supplies a single-line command (see
`/commands/`). The agent must be deterministic, conservative with secrets, and produce both human-readable
artifacts and a strict JSON summary.

Operational rules

- Work on branch `sys-billing/sapi-to-sys-<id>`. If git is unavailable, write patch files to `sys-billing/patches/`.
- Always produce incremental commits with one logical change per commit. If automated commits aren't possible, create
  patch files named `sys-billing/patches/0001-...patch`.
- Do NOT store secrets; preserve placeholders in `secrets.yaml` or kustomize overlays.

Step-by-step tasks for `generate:all` (the agent must follow this order):

1. Consolidate pom: read root `pom.xml` and any poms in source and produce a merged `pom.xml` under a new commit (
   document changes in `sys-billing/report.md`).
2. Config conversion: convert mule properties/mule-app.properties into `src/main/resources/application.yml`. Create
   kustomize patches under `kustomize/environments/*` where needed.
3. Code conversion: for each Mule flow exposing an HTTP endpoint create a `@RestController` with routes matching the
   original path. For transforms create `mapper` classes and unit tests.
4. Tests: add/convert unit tests into `src/test/java` and fixture data into `src/test/resources`.
5. Docker & kustomize: ensure `Dockerfile` and `docker-compose.yml` work with the new jar and add kustomize patches for
   environments.
6. Build & validate: run `mvn -B -DskipTests=false clean test package`. Save logs to `sys-billing/build.log` and
   include failed tests in `sys-billing/report.md`.

Output requirements (always produce these files/sections):

- `sys-billing/report.md` (human readable summary)
- `sys-billing/build.log` (build output, if build ran)
- `sys-billing/patches/` or commits representing code changes

JSON summary schema (agent must include this JSON in the final message):
{
"command": "generate:all|generate:flow|report",
"mappings": [ { "src": "path", "dst": "path", "notes": "text" } ],
"changes": ["short text of changes"],
"build": { "ran": true|false, "result": "SUCCESS|FAILURE|SKIPPED", "log_path": "sys-billing/build.log" },
"needs_input": [ "if any missing data requested" ]
}

Manual items and TODOs

- If a Mule flow involves complex orchestration or external secrets the agent cannot safely convert, create
  `sys-billing/manual/<name>.todo.md` with precise instructions and sample inputs.

Error handling

- If the agent encounters parsing errors in Mule XML or cannot map an element, it must add an entry into
  `sys-billing/report.md` and include `needs_input` in the JSON.

End of agent_task.md


