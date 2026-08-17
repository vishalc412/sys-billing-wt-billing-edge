do {
  ns soapenv http://schemas.xmlsoap.org/soap/envelope/
  ---
  {
    soapenv#"Envelope": do {
      ns soapenv http://schemas.xmlsoap.org/soap/envelope/
      ---
      {
        soapenv#"Body": {
          "PrimaryAccountResponse": {
            "responseHeader": {
              "status": "success"
            },
            "primaryAccount": {
              "accountBalance": "2540.8100",
              "accountNumber": "6000077687",
              "agencyCode": "121377",
              "billingSystemCode": "DuckCreek",
              "billTocontact": {
                "name": "2941_Repro",
                "addressLine1": "1200 East St",
                "addressLine2": null,
                "city": "Tiskilwa",
                "state": "IL",
                "zip": "61368"
              },
              "billType": "PA",
              "dateBilled": "2024-09-17",
              "eftEstablished": "false",
              "futureInstallments": {
                "amount": "206.7400",
                "dueDate": "2024-11-07",
                "invoiceDate": "2024-10-18"
              },
              "futureInstallments": {
                "amount": "206.7400",
                "dueDate": "2024-12-07",
                "invoiceDate": "2024-11-15"
              },
              "installmentFees": "6.0000",
              "pastDueAmount": "212.7400",
              "pastDueDate": "2024-10-07",
              "policies": {
                "balance": "2534.8100",
                "currentAmountDue": "0",
                "policyNumber": "543003V",
                "policyTerm": {
                  "effectiveDate": "2024-10-07",
                  "expirationDate": "2025-10-07",
                  "insuredAccount": {
                    "balance": "2534.8100",
                    "cancellationNoticeCount": "0",
                    "currentAmountDue": "0",
                    "dateBilled": "2024-09-17",
                    "lastPaymentApplied": "0",
                    "pastDueAmount": "206.7400",
                    "pastDueDate": "2024-10-07",
                    "paymentStatus": null,
                    "premium": "2534.8100",
                    "totalCreditsApplied": "0",
                    "totalPaymentsApplied": "0",
                    "transactions": {
                      "amountDue": "2480.8100",
                      "description": "New Business",
                      "dueDate": "2024-10-07",
                      "processingDate": "2024-07-24"
                    },
                    "transactions": {
                      "amountDue": "54.0000",
                      "description": "Endorsement",
                      "dueDate": "2025-01-07",
                      "processingDate": "2024-07-24"
                    }
                  },
                  "lumpSumIndicator": "false",
                  "paymentPlan": "Monthly",
                  "status": "Active"
                }
              },
              "transactions": {
                "amountDue": "212.7400",
                "description": "Invoice",
                "dueDate": "2024-10-07",
                "processingDate": "2024-09-17",
                "feeTransactionParts": null,
                "policyTransactionParts": null
              }
            }
          }
        }
      }
    }
  }
}