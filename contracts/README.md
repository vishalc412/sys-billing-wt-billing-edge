
# contracts/ — frozen at S3

One OpenAPI document per owned module. **These files are frozen at the `scaffold-v1` tag and are
read-only for the whole of S4.** They are the coordination mechanism that lets three agents build in
parallel without talking to each other, and a contract written by its own implementer is not a
contract.

| Contract | Module | Endpoints |
|---|---|---|
| `billing-edge/openapi.yaml` | `services/billing-edge` | `/info`, `/console/*`, plus the canonical security scheme, correlation headers and contract-violation bodies |
| `billing-account/openapi.yaml` | `services/billing-account` | `/primaryAccount`, `/primaryAccount/transactions`, `/primaryAccount/policy/escrow/transactions` |
| `billing-agency/openapi.yaml` | `services/billing-agency` | `/externalPrimaryAccount/{id}/account-billing`, `/externalPrimaryAccount/{id}/policy-billing`, `/pastDueToday/{agencyCode}`, `/pendingCancelToday/{agencyCode}` |

## There is no asyncapi.yaml, and that is a decision

ADR-0002. Every operation in the legacy system is a synchronous GET and every backend interaction is
a synchronous request/response call. There is no queue, no scheduler, no publisher and no consumer.
The `ibm-mq` namespace and the MQ client that appear in the legacy POM are dead (ADR-0040). An
absence that is not recorded gets re-invented by someone who assumes there must have been something.

## Reading the markers

| Marker | Meaning |
|---|---|
| `x-legacy-defect: ADR-00NN` | This part of the contract documents a known legacy defect that an ADR decided to preserve. It is not a mistake in the contract. |
| `x-unknown: R-0NN` | The legacy behaviour here could not be determined. The documented shape is an assumption owned by an ADR and tracked by that risk. |
| `x-name-source: inferred` | The field name is inferred from the knowledge map's prose rather than pinned by a captured payload. Confirm against the baseline (PAR-001). |
| `x-migrated-from` | The Mule artefact or knowledge-map node this element reproduces. |

## If the contract is wrong

Raise it as a **contract defect** to the migration-architect. Do not edit the file, do not work
around it in code, and do not implement something the contract does not describe. A contract
amendment is re-versioned and re-dispatched to every affected packet, which is cheap; a silent
divergence between two modules' idea of the same shape is not.
