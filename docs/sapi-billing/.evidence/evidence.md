## Platform baseline

_The ground the feature stands on. An HLD that omits this describes a component floating in space._

- **Build:** `pom.xml`

# Repo digest — C:\Users\kavita.batra\Downloads\Westfiled\Agentic\sys-billing\archive\sapi-billing

## Stack
- Java (Maven) — pom.xml
-   version==1.0
-   modelVersion==4.0.0
-   version==1.0.0
-   app.runtime==4.4.0-20250116
-   mule.maven.plugin.version==3.5.4
-   munit.version==2.1.4
-   version==9.1.1.0
-   version==2.3.4
-   version==1.2.3
-   version==2.0.2
-   version==1.0.12
-   version==1.7.3
-   version==1.2.2
-   version==1.5.6
-   version==2.0.23
-   version==1.6.0
-   version==2.3.9

## Layout
- src/main/  (60 files)
- src/test/  (33 files)
- (root)/  (7 files)
- reports/assets/  (2 files)
- .mule/  (1 files)
- .settings/  (1 files)
- reports/  (1 files)

## Guardrails
_constraints, transactions, retries, timeouts — note what is ABSENT here, that is usually the finding_
- src\main\resources\dev.properties:10  10000
- src\main\resources\local.properties:10  10000
- src\main\resources\perf.properties:9  10000
- src\main\resources\prod.properties:20  20000
- src\main\resources\stage.properties:9  10000

## Infrastructure
- .tfignore

## Coverage
entrypoint=0, data=0, integration=0, config=0, guardrail=5, tests=0, infra=1, docs=0, stack=18

Open only the files this digest makes you curious about.