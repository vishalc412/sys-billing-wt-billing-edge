# CLAUDE.md

This file provides project-specific instructions for Claude agents working in this repository.
Claude reads this file automatically at the start of every session.

> Rewritten from the original CLAUDE.md to match the **actual** `sys-billing` codebase (verified
> against the real repo, not assumed). See `GAP_ANALYSIS.md` for the full list of corrections.

---

## Project Context

| Item | Value |
|---|---|
| **Source (Mule)** | `archive/sapi-billing/sapi-billing/source/sapi-billing/` |
| **Target (Spring Boot)** | `src/` (code lands directly under `src/main/java`, not in a staging folder) |
| **Build tool** | Maven (`pom.xml`) |
| **Java version** | 21 |
| **Spring Boot** | 3.5.14 (`spring-boot-starter-parent`) |
| **GroupId / ArtifactId** | `com.westfield.api` / `sys-billing` |
| **Base package** | `com.westfield.api.billing` |
| **Deployment** | Kubernetes via Kustomize (`kustomize/`) |

### Primary Goal

Convert `sapi-billing` (Mule 4) to `sys-billing` (Spring Boot 3) while preserving all project
standards and producing auditable, atomic migration artifacts. This service is a pure
**integration/orchestration API** — it has **no database and no persistence layer**. It forwards
requests to a downstream **SOAP** billing service and to downstream **REST** services.

---

## Build & Test Commands

```bash
mvn clean compile
mvn clean test
mvn clean package -DskipTests
mvn -B -DskipTests=false clean test package
mvn test -Dtest=PrimaryAccountControllerTest
mvn clean test jacoco:report        # → target/site/jacoco/index.html
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=local"
```

> Save build output to `generated/sys/build.log` whenever a migration command triggers a build
> (see "Migration Output Location" below).

---

## Architecture Overview

The project is **feature-sliced**, not layer-sliced. Each business capability gets its own
package containing its own controllers/dtos/services/mappers, mirroring one or more Mule flows:

```
com.westfield.api.billing/
├── primaryAccount/                 ← src/main/mule/implementation/primaryAccount-implementation.xml
│                                      + primaryAccountTransactions-implementation.xml
│                                      + escrowTransactions-implementation.xml
│   ├── controllers/                 PrimaryAccountController, PrimaryAccountEcsrowTransactionsController
│   ├── dtos/request/                PrimaryAccountRequest, EscrowTransactionsRequest
│   ├── dtos/response/               PrimaryAccountResponse, TransactionResponse, EscrowTransaction, ...
│   ├── services/                    PrimaryAccountService, PrimaryAccountEscrowTransactionsService,
│   │                                 PingService, TemplateService
│   ├── mappers/                     hand-written mapper classes (NOT MapStruct — plain Java, manual field mapping)
│   ├── entities/                    JAXB request wrapper objects used to build the SOAP call
│   ├── configs/                     BillingServiceProperties, SamlProperties (@ConfigurationProperties)
│   ├── exceptions/                  DownStreamServiceException, NoContentFoundException, SamlTokenException
│   └── utils/                       XmlUtil, SoapUtil, DateUtil
│
├── pastDueToday/                    ← same shape: controllers / dtos / mappers / services
├── pendingCancellation/             ← same shape
├── externalPrimaryAccount/          ← same shape (AccountBillingController, PolicyBillingController)
│
├── configs/                         SHARED, not feature-scoped:
│                                       SecurityConfig, Resilience4jConfig, RestClientConfig,
│                                       CachingConfig, WebConfig, OpenTelemetryConfig,
│                                       CustomAuthenticationEntryPoint, ServiceProperties
│                                       configs/interceptors/GlobalExceptionHandler
├── entities/ErrorResponse.java      SHARED error payload
└── services/RestClientService.java  SHARED outbound REST helper (wraps Spring RestClient)

com.westfieldgrp.billing/            GENERATED — JAX-WS/JAXB stubs from BillingService.wsdl.
                                      Never hand-edit; regenerate from the WSDL if it changes.
                                      Contains request/response beans, ObjectFactory, fault types.

Config:  src/main/resources/application.yml  (+ kustomize overlays per env)
Tests:   src/test/java/  (mirrors main structure, e.g. .../primaryAccount/controllers/PrimaryAccountControllerTest.java)
```

There is intentionally **no `repository/` package, no `client/` package, no `@Entity`/JPA**.
Do not introduce a persistence layer unless a specific Mule flow genuinely requires one (none
currently do).

### Downstream integration patterns

- **SOAP**: `BillingService.wsdl` (in `schemas/`) is the contract for the core billing system.
  Apache CXF generates a JAX-WS client into `com.westfieldgrp.billing`, but **that generated
  client is not actually invoked anywhere** — it's unused/dead code. The real call is a **manual
  XML-over-HTTP POST**: build the JAXB request bean → marshal to XML (`XmlUtil`) → splice into a
  SOAP envelope template (`TemplateService` + `template/billing_service_payload.xml`) → POST via
  `RestClient` → unmarshal the response fragment back into a JAXB type (`SoapUtil`). Calls are
  authenticated with a **SAML token** fetched per-request from Ping's STS (`PingService`,
  `SamlProperties`, `SamlTokenException`, `template/saml_token_request.xml`). See
  `MULE_CONVERSION_GUIDE.md` §3 for the full sequence — follow it exactly for new SOAP-backed
  flows rather than wiring up the generated JAX-WS port.
- **REST**: outbound REST calls go through `RestClientService`, built on Spring's `RestClient`
  (not `RestTemplate`), configured in `RestClientConfig`, wrapped with Resilience4j
  (`@CircuitBreaker` + `@Retry` + fallback) as documented in `.github/restclient-standard.md`.
- **Caching**: `CachingConfig` configures Caffeine (`spring-boot-starter-cache` + `caffeine`) for
  results that don't need to hit the downstream service on every request.
- **Templates**: `src/main/resources/template/*.xml` (SOAP envelope / SAML request templates) are
  filled in at runtime with Apache Commons Text `StringSubstitutor` — see `TemplateService`.

Tech stack: **Spring Boot 3, Spring Security (OAuth2 client + resource server, Ping as IdP),
Apache CXF JAX-WS + JAXB (SOAP client), Spring `RestClient` (REST client), Caffeine caching,
Resilience4j (circuit breaker + retry), Lombok, springdoc-openapi, Micrometer + OpenTelemetry
(OTLP), JUnit 5, Mockito.**

**Not used** — do not add these unless a new requirement genuinely needs them: Spring Data JPA
(dependency exists in `pom.xml` but is commented out), Spring JMS, MapStruct, TestContainers.

---

## Key Files and Directories

| Path | Purpose |
|---|---|
| `archive/sapi-billing/sapi-billing/source/sapi-billing/src/main/mule/` | Source Mule flow XML files |
| `archive/sapi-billing/sapi-billing/source/sapi-billing/src/main/resources/` | Source Mule properties files |
| `schemas/BillingService.wsdl` (+ `.xjb`, `.xsd`) | SOAP contract for the core billing system — drives `com.westfieldgrp.billing` codegen |
| `src/main/java/com/westfield/api/billing/` | Hand-written Spring Boot source (feature packages, see above) |
| `src/main/java/com/westfieldgrp/billing/` | Generated JAX-WS/JAXB SOAP client classes — do not hand-edit |
| `src/main/resources/application.yml` | Main Spring configuration |
| `src/main/resources/template/` | SOAP/SAML XML templates filled at runtime |
| `src/test/java/` | Unit and integration tests (`*Test.java`, `*ITTest.java`) |
| `.github/controller-standards.md` | Controller conventions (generic, shared across projects — see note below) |
| `.github/service-standards.md` | Service layer conventions |
| `.github/restclient-standard.md` | RestClient / circuit breaker conventions |
| `.github/request-response-standards.md` | Request/Response DTO conventions |
| `MULE_CONVERSION_GUIDE.md` | Mule → Spring mapping guide (repo root) |
| `CONFIG_MAPPING.md` | Properties → YAML / Kustomize mapping guide (repo root) |
| `PROJECT_STRUCTURE.md` | Project layout reference (repo root) |
| `TESTING_STRATEGY.md` | Testing patterns (repo root) |
| `TROUBLESHOOTING.md` | Troubleshooting guide (repo root) |
| `kustomize/base/secrets.yaml` | Secret placeholders (never real values) |
| `kustomize/environments/` | Per-environment patches (dev/test/stage/perf/prod) |
| `generated/sys/` | Scratch output area from migration runs — see "Migration Output Location" below; **not** authoritative source |

> **Note on `.github/*.md` standards docs**: these are generic, org-wide standards shared across
> multiple migration projects — their example code uses a different domain (Watchlist/OFAC), not
> billing. Follow their *conventions* (annotations, error handling, DI style, OpenAPI docs), but
> for billing-specific concerns (SOAP client pattern, SAML auth, feature-package layout, caching)
> follow this file instead, since the generic standards don't cover them.

---

## Migration Commands

This repo does **not** use `.claude/commands/*.md` slash commands. Command behavior is defined by
three files at the repo root: `single_prompt.md` (the command syntax), `agent.md` (schemas the
agent must produce), and `auto_agent.md` (step-by-step behavior). Use this single-line syntax:

| Command | Purpose |
|---|---|
| `generate:all` | Full migration: inventory → config → code → tests → Docker → build |
| `generate:module <name>` | Convert a top-level module/folder from the source |
| `generate:flow <basename>` | One Mule flow → Controller + Service + Mapper + Tests |

### Usage examples

```
generate:all
generate:flow primaryAccount-implementation
generate:module primaryAccount
```

Full reference: `single_prompt.md`, `agent.md`, `auto_agent.md`.

---

## Migration Output Location

⚠️ Every prior version of this doc (and `agent.md`/`auto_agent.md`) said outputs go under
`sys-billing/...`. In the real repo, the one migration run that was captured actually wrote to
**`generated/sys/`** instead — and that folder is stale (it only contains 2 Java files that don't
match any class name in `src/`). Until this is reconciled with the team, follow this rule:

- Write scratch/inventory/report artifacts to **`generated/sys/`**:
  - `generated/sys/build.log` — Maven output (only when a build runs)
  - `generated/sys/manual/*.todo.md` — manual steps for unmappable flows
- Write the **actual production code** directly to `src/main/java/com/westfield/api/billing/...`
  and `src/test/java/...` — do **not** stage final code under `generated/sys/src/`. The real,
  merged history shows code committed straight into `src/`.
- Do not create a `sys-billing/patches/` folder — it has never been used. If git commits aren't
  possible in your environment, ask before falling back to patch files.

---

## Coding Rules

- **Constructor injection only** — never field-level `@Autowired`
- **One logical change per commit** — keep migrations atomic and reviewable
- **Branch naming** — the real history uses ticket-style names, e.g. `mmi-<ticket>-<shortdesc>`
  (`mmi-225-primaryAccount`, `mmi-1311-BillingInfoLevel`) or short descriptive names
  (`billingIssueFix`, `escrowTransactionsVersionIssue`, `Api_cleanup`). Follow this convention,
  not a generic `sys-billing/sapi-to-sys-<id>` pattern.
- **No hardcoded secrets** — use `${ENV_VAR:default}` in YAML; real values go in Kustomize secrets
- **Java 21** — use records, text blocks, and pattern matching where appropriate
- **Lombok** — use `@Data`, `@Builder`, `@Slf4j`, `@RequiredArgsConstructor`
- **Resilience4j** — wrap every outbound REST/SOAP call with `@CircuitBreaker` + `@Retry` + fallback
- **Mappers are hand-written** — do not introduce MapStruct; follow the existing plain-class
  mapper pattern (see `primaryAccount/mappers/PrimaryAccountRequestMapper.java` for the style)
- **No persistence layer** — do not add JPA entities/repositories unless a flow genuinely requires
  durable storage (none currently do)
- **Test coverage** — minimum 80% overall; note `pom.xml`'s JaCoCo exclude currently says
  `**/dto/**` but the real folders are `dtos/request/` and `dtos/response/` (plural) — flag this
  mismatch rather than silently relying on the exclude working
- **Preserve Mule XML** — never delete `archive/` files; they are the source of truth

---

## Safety Rules

- **Never expose secrets** — no passwords, tokens, or keys in source files
- **Secrets placeholder pattern** — `{{ PLACEHOLDER }}` in `kustomize/base/secrets.yaml`
- **No destructive operations** — do not delete or overwrite `archive/` contents
- **Never hand-edit `com.westfieldgrp.billing`** — it's generated from `schemas/BillingService.wsdl`
- **Branch discipline** — use a ticket-style branch name (see Coding Rules); confirm before pushing
