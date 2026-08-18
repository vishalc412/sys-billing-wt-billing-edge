---
id: DEF-0105
title: "Configured billing.truststore.* is validated at startup but never loaded — STS/vault clients use the JVM default SSLContext (ADR-0041 unimplemented)"
service: billing-edge
severity: medium
class: implementation
raised_at_stage: S5
summary: >
  CFG-002-e/f/g and TOK-001-h assert the server certificate is validated against the configured
  trust material and ADR-0041 requires the Thycotic client to be given the resolved truststore
  password. The S4 evidence only asserted the configuration half (billing.truststore.location and
  .password are declared in every profile and not blank). At runtime the Thycotic and STS HttpClients
  are built with no SSLContext, so they use SSLContext.getDefault() (JVM cacerts). A truststore
  location pointing at a file that does not exist changes nothing. No production class references
  SSLContext, KeyStore, or TrustManagerFactory. ADR-0041 is unimplemented.
reproduction: >
  Construct ThycoticCredentialProvider and WsTrustSecurityTokenService with BillingEdgeProperties
  whose truststore location is "classpath:truststore/does-not-exist-anywhere.jks" and password is
  "a-resolved-per-environment-password". Reflectively read each adapter's httpClient field and
  assert client.sslContext() is SSLContext.getDefault() (the JVM default) — i.e. the configured
  truststore is ignored. Structurally, walk src/main/java and assert no class references
  SSLContext, KeyStore, or TrustManagerFactory. The S5 test asserts these observations and names
  DEF-0105 in the @DisplayName: "the Thycotic client uses the JVM DEFAULT SSLContext, not
  billing.truststore.location".
evidence:
  - "test:com.westfield.api.billing.edge.s5.S5OutboundTrustProbeTest#theVaultClientIgnoresTheConfiguredTruststore"
  - "test:com.westfield.api.billing.edge.s5.S5OutboundTrustProbeTest#theStsClientIgnoresTheConfiguredTruststore"
  - "test:com.westfield.api.billing.edge.s5.S5OutboundTrustProbeTest#noProductionClassEverLoadsTheTruststore"
  - "evidence:run-s5-billing-edge-8f2eec88"
  - "adr:ADR-0041"
traces_to:
  - "mule:flow/sapi-billing-search-main"
  - "test:com.westfield.api.billing.edge.s5.S5OutboundTrustProbeTest#theVaultClientIgnoresTheConfiguredTruststore"
  - "evidence:run-s5-billing-edge-8f2eec88"
status: open
blocks_gate: false
triage:
  round: 2
  disposition: >
    Implementation defect. The outbound TLS trust posture is declared in configuration and validated
    at startup, but never applied: the STS and vault HttpClients are built without an SSLContext and
    therefore use the JVM default trust (cacerts), not the configured billing.truststore.* material.
    ADR-0041 (give the Thycotic client the resolved truststore password and actually load the
    truststore) is unimplemented. CFG-002-g (rotation takes effect without a redeploy) is also
    unsatisfied in principle because no trust material is loaded at all. The connection is still
    TLS-encrypted and validated against cacerts, so this is a trust-posture gap (broader trust than
    configured), not an active plaintext/auth bypass. Autonomously fixable by a java-engineer
    re-dispatch. Round 2 of the bounded loop.
  action: >
    Re-dispatch to java-service-engineer (billing-edge): load billing.truststore.location/.password
    into a KeyStore, build an SSLContext from it, and configure the STS and vault HttpClients with
    that SSLContext (and the Thycotic client with the resolved truststore password per ADR-0041).
    The three S5 tests theVaultClientIgnoresTheConfiguredTruststore,
    theStsClientIgnoresTheConfiguredTruststore, and noProductionClassEverLoadsTheTruststore must
    flip from asserting "uses SSLContext.getDefault() / no class loads KeyStore" to asserting the
    configured SSLContext is in use. Preserve CFG-002-i (no client certificate presented) — that
    holds today vacuously and must hold by construction after the fix.
  decided_by: migration-architect
---

# DEF-0105: the configured trust material is never loaded by anything

Severity:  medium
Found in:  S5 verify of WP-001, billing-edge
Service:   billing-edge
Criterion: CFG-002-e/f/g, TOK-001-h
Layer:     outbound TLS

## What is wrong

The S5 probe `@DisplayName`:

> "the Thycotic client uses the JVM DEFAULT SSLContext, not billing.truststore.location"

The S4 evidence for CFG-002-e/f/g and TOK-001-h was `EnvironmentProfileTest` and
`StartupConfigurationValidatorTest`, which assert `billing.truststore.location` and
`billing.truststore.password` are declared in every profile and not blank — the **configuration**
half. The S5 probe asks the **runtime** half: is the material actually used? It is not.

- `ThycoticCredentialProvider.httpClient.sslContext()` is `SSLContext.getDefault()` (JVM cacerts).
- `WsTrustSecurityTokenService.httpClient.sslContext()` is `SSLContext.getDefault()`.
- No class in `src/main/java` references `SSLContext`, `KeyStore`, or `TrustManagerFactory`.

A `billing.truststore.location` pointing at a file that does not exist changes nothing.

## Severity rationale

**Medium.** Outbound TLS trust is not enforced against the configured material; the STS/vault
connections validate against the JVM default trust (cacerts), which is broader than the configured
truststore. This is a trust-posture gap, not a plaintext or active auth bypass — the connections
are still TLS-encrypted and certificate-validated against cacerts. ADR-0041 (the Thycotic client
gets the real truststore password, after the truststore is inspected) is unimplemented. Not a gate
blocker on severity.

## Authoritative basis

ADR-0041 (Thycotic truststore password resolution) — Accepted, pending human approval. CFG-002-e
("the server certificate is validated against the configured trust material and an untrusted
certificate is refused"), CFG-002-f, CFG-002-g (rotation without redeploy), TOK-001-h.

## Disposition

Class: **implementation**. Re-dispatch to the billing-edge java-service-engineer. Round 2 of the
bounded loop. Status remains **open**.

## Trace chain

`mule:flow/sapi-billing-search-main` (the main flow that calls the STS and the vault) →
`adapter.out.sts.WsTrustSecurityTokenService` + `adapter.out.vault.ThycoticCredentialProvider`
(httpClient built with no SSLContext) →
`test:S5OutboundTrustProbeTest#theVaultClientIgnoresTheConfiguredTruststore` →
`evidence:run-s5-billing-edge-8f2eec88` · `adr:ADR-0041`

## Related

DEF-0100 (the same two adapter classes cannot be instantiated by Spring). Fixing DEF-0100 unblocks
the context; DEF-0105 is the runtime TLS behaviour of the HttpClient those adapters build.