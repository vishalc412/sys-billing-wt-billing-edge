# ADR-0018: Configuration values are migrated verbatim; structurally broken configuration fails at startup

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0018-environment-configuration-parity.md`

## Context

Six recorded configuration facts, each small, collectively a decision: `mule.env` defaults to `dev`
so an application started without it silently runs against development back ends (N-0002);
`local.properties` omits `primaryAccount.host`/`path` so three endpoints cannot start under `local`
(N-0003, N-0101); `stage.properties` points at **`tst2esb`** — stage calls the test ESB (N-0101);
`prod.properties` defines a **test** PingFederate hostname referenced by nothing (N-0010);
`pasm.id.billing` is defined in all six environments and referenced nowhere (U15, R-022); and
`response.timeout` is 10 s everywhere but 20 s in prod, so no lower-environment latency result is
representative (N-0003 ec).

Plus: four required properties exist in no file (U11, R-014), and `buildInfo.properties` ships with
unsubstituted Maven filter tokens, so an unfiltered build makes `/info` report `@pomBuildNumber@`
rather than falling back to its defaults (N-0011 ec, N-0044 ec).

## Decision

Values migrate verbatim, environment for environment. Structure fails fast:

1. **No environment default.** The application refuses to start unless the profile is set
   explicitly. The silent `dev` fallback is not carried forward.
2. **Required backend configuration is validated at startup**, not at first request. Missing
   `primaryAccount.host` fails startup with a message naming the property.
3. **Stage keeps pointing at the test ESB**, unchanged, and logs a startup WARN naming the resolved
   backend host per environment. It is flagged, not corrected — see rejected alternatives.
4. The unreferenced **test PingFederate hostname in `prod`** is dropped.
5. `pasm.id.billing` is **carried forward verbatim** pending R-022, rather than dropped.
6. Build metadata substitution must actually run; an unsubstituted token is a build failure, not a
   runtime surprise.

Everything in this decision happens **before the first request**. No consumer can observe any of it.

## Rejected alternatives

**A — migrate the property files verbatim including the structural faults.** Rejected: the silent
`dev` fallback and the deferred missing-host failure are each worth a day of confusion at cutover,
and neither is observable behaviour anyone could depend on.

**C — correct the values as well: point stage at `stageesb`, and so on.** Rejected: stage pointing at
the test ESB is **not obviously a mistake** — the stage ESB may not exist or may have no data
(R-023). Correcting it could break the stage environment outright, and the knowledge map cannot tell
us which case holds. The WARN makes it visible without changing it.

**Dropping `pasm.id.billing` as unreferenced (the candidate's recommendation).** Rejected: a property
defined in six files and used in none is exactly the shape of something an external process greps
for. It costs nothing to keep and is irreversible to drop wrongly. R-022 remains open.

## Consequences

+ Two classes of cutover-day confusion are eliminated before they happen.
+ Configuration drift between environments becomes visible in the startup log.
− Stage test results still say nothing about the stage ESB, and now we have written down that we
  knew.
− Refusing to start without an explicit profile will break any deployment script that relied on the
  default. That is a deliberate, loud break, chosen over a silent wrong-backend connection.
− Keeping `pasm.id.billing` carries an unexplained property into a new system.

## Verification

- CFG-001 criteria: startup fails with no profile; startup fails with a missing backend host; startup
  WARN names the resolved hosts.
- INF-001-c: `/info` never returns an unsubstituted token; the build fails first.

## Traces to

`km:node/N-0002, N-0003, N-0010, N-0011, N-0044, N-0074, N-0101, N-0102` ·
`spec:capability/CAP-012, CAP-013` · `risk:R-014, R-022, R-023, R-026`
