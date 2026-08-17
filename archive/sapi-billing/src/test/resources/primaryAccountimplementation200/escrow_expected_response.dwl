%dw 2.0
import * from dw::test::Asserts
---
payload must equalTo({
  "accountBalance": 1363.00,
  "accountNumber": "3476501380",
  "billTocontact": {
    "name": "DIFFERENT BILLED",
    "address": {
      "street": "15 W MARKET ST",
      "city": "AKRON",
      "state": "OH",
      "zip": "44308"
    }
  },
  "billingSystemCode": "BCMS",
  "billType": "N",
  "dueAmount": 0.00,
  "dueDate": null,
  "dateBilled": null,
  "lastPaymentAmount": null,
  "lastPaymentDate": null,
  "eft": "false",
  "pastDueAmount": null,
  "pastDueDate": null,
  "policies": [
    {
      "policyNumber": "543793R",
      "hasEscrow": false
    },
    {
      "policyNumber": "543791G",
      "hasEscrow": true
    }
  ]
})