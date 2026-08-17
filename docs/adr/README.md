
# Accepted ADRs

44 accepted decision records, produced at stage S3 of the MuleShift pipeline on 2026-08-14 from 42
candidates raised at S2, plus two decisions that arose from the state of this repository
(ADR-0043, ADR-0044).

**Never renumbered, never deleted.** A superseded ADR stays, with a pointer forward.

## Where to start

| If you want to know | Read |
|---|---|
| Why the repository looks like this | ADR-0001, ADR-0044 |
| What happened to the code that was already here | ADR-0043 |
| Why a defect was kept | ADR-0012 first, then the specific ADR-0023 … ADR-0042 |
| What we do not know | ADR-0013, ADR-0016, ADR-0021, ADR-0037 (all marked assumption-based) |

## Dispositions of the recorded legacy defects

| ADR | Defect | Disposition |
|---|---|---|
| 0023 | escrow `policyNumber` carries the account number | preserve |
| 0024 | escrow ignores the caller's policy parameters | **correct** (confidentiality) |
| 0025 | escrow `amountDue` is a string | preserve |
| 0026 | escrow returns two incompatible shapes | preserve |
| 0027 | escrow contradicts itself on "not found" | preserve |
| 0028 | mapper chosen by account-number prefix | preserve + instrument |
| 0029 | `eft` is a string, declared boolean | preserve |
| 0030 | BCMS `billingStatus` can be an array | preserve + instrument |
| 0031 | external 204 keeps the fault body | **correct** |
| 0032 | transactions answer a fault with 200 + `[]` | preserve + instrument |
| 0033 | backend status relayed verbatim | preserve + failure-origin header |
| 0034 | inert `targetValue` on eight call sites | nothing to preserve |
| 0035 | catch-all logs a stale status | **correct** (log-only) |
| 0036 | two disagreeing impersonation definitions | preserve, both implemented once |
| 0037 | no agency entitlement check | **correct** (security) |
| 0038 | console enabled in production | **correct** (security) |
| 0039 | identical secure-properties ciphertext in all environments | **correct** (security) |
| 0040 | dead IBM MQ dependency | removed |
| 0041 | Thycotic truststore password reference | **correct**, after inspection |
| 0042 | published `commonError` matches nothing emitted | preserve, contract regenerated |
