# ADR-0006: Backend access is an anti-corruption layer over a plain HTTP client and XML templates

Status:   Accepted (pending human approval at the S3 gate)
Date:     2026-08-14
Deciders: migration-architect, [human approver]
Supersedes: —
Candidate: `out/adr-candidates/ADR-CANDIDATE-0006-soap-connector-replacement.md`

## Context

Both back ends are consumed as raw HTTP POSTs of hand-built XML, not through Mule's SOAP connector,
and **no WSDL or XSD exists anywhere in the source** (U8, R-017). The wire details differ per
backend and are observable: the WAS calls send `Content-Type: */*` (not a valid media type; "the
backend evidently tolerates it", N-0017 ec) and omit null elements (`skipNullOn="everywhere"`),
while the ESB call sends `application/xml`, a hardcoded `wfesb` routing header, and **empty strings**
instead of omitting absent elements (N-0081). Neither sends a SOAPAction header; both emit a
single-line envelope with no XML declaration.

## Decision

Per backend, one **port** in `domain`/`application` speaking the business operation
(`PrimaryAccountEnquiryPort`, `ExternalAccountPort`, `AgencyWorklistPort`), and one adapter in
`adapter/out` implementing it with a plain HTTP client plus an explicit XML template per operation.
No generated SOAP stack.

The following wire properties are **stated per adapter and asserted by test**, because each is
invisible in any single legacy file:

| | ESB adapter (`billing-account`) | WAS adapter (`billing-agency`) |
|---|---|---|
| `Content-Type` | `application/xml` | `*/*` — reproduced deliberately |
| Absent optional element | sent as an **empty element** | **omitted** |
| SOAPAction | none | none |
| Envelope | single line, no XML declaration, no indentation | same |
| Extra header | `wfesb` routing header, `Billing.primaryAccount` 1.1, dialect `Billing` 1.0 | none |
| Timeout | ADR-0014 | ADR-0014 |

WS-Security header construction lives once, in `billing-edge` behind
`BackendAssertionProvider.assertionHeader()`, and is inserted by each adapter's template.

## Rejected alternatives

**B — generated JAX-WS clients from recovered WSDLs.** Rejected: the WSDLs do not exist and R-017
has no owner and no date. Worse, a generated client would normalise `Content-Type`, emit an XML
declaration and re-indent the envelope — four observable changes to a wire format the map says the
backend merely "tolerates". Tolerance is not a contract, and discovering that at cutover is exactly
the failure this migration must avoid. If both WSDLs are recovered *and* the backend owners confirm
formatting is insignificant, this becomes the better option and the ports make it a drop-in change.

**A — plain HTTP client and templates with no port abstraction.** Rejected: it leaves the wire
oddities smeared across the mappers, which is how the legacy ended up with two null conventions
nobody could see. One extra interface per backend is a small price for quarantining them.

## Consequences

+ Byte-level parity is achievable, and each oddity is a named test rather than a framework accident.
+ The mappers get a stable input contract regardless of what the wire does (ADR-0005).
+ Replacing the transport later touches one class per backend.
− No compile-time contract with either back end. A backend field rename surfaces as a `null` at
  runtime, exactly as today. We are preserving that exposure knowingly, because the alternative
  requires an artifact that does not exist.
− Hand-rolled WS-Security header construction is security code written by us. It is one place, it is
  tested, and it is still security code we did not previously own.
− Reproducing `Content-Type: */*` will be flagged by every reviewer and by some HTTP clients.

## Verification

- Integration (WireMock): request-matching assertions on `Content-Type`, absence of SOAPAction,
  single-line body, `wfesb` header presence/absence, and empty-element-versus-omitted for each
  adapter. Criteria ESB-002, ESB-003, EXT-002, EXT-006, AGY-002, AGY-004.
- ArchUnit: no `adapter/out` type is referenced from `domain`.

## Traces to

`km:node/N-0005, N-0017, N-0018, N-0054, N-0055, N-0059, N-0060, N-0064, N-0065, N-0074, N-0081,
N-0082` · `spec:capability/CAP-008, CAP-009, CAP-010` · `risk:R-017`
