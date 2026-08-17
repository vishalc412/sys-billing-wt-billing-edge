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
							"accountBalance": "1363.00",
							"accountNumber": "3476501380",
							"agencyCode": "340470",
							"billingSystemCode": "BCMS",
							"billTocontact": {
								"name": "DIFFERENT BILLED",
								"addressLine1": "15 W MARKET ST",
								"city": "AKRON",
								"state": "OH",
								"zip": "44308"
							},
							"billType": "N",
							"currentAmountDue": "1363.00",
							"currentAmountDueDate": "2025-04-03",
							"dateBilled": "2025-03-13",
							"eftEstablished": "false",
							"policies": {
								"balance": "0.00",
								"currentAmountDue": "0.00",
								"escrowBalance": "958.00",
								"policyNumber": "543791G",
								"policySymbol": "HOP",
								"policyTerm": {
									"effectiveDate": "2025-03-03",
									"escrowAccount": {
										"balance": "958.00",
										"cancellationNoticeCount": "0",
										"currentAmountDue": "958.00",
										"currentAmountDueDate": "2025-04-03",
										"dateBilled": "2025-03-13",
										"loanNumber": "LN#56454-015645",
										"mortgagee": {
											"name": "CAPITAL SAVINGS AND LOAN",
											"addressLine1": "PO BOX 12",
											"city": "NEWTON FALLS",
											"state": "OH",
											"zip": "44444-0012"
										},
										"paymentStatus": "Current",
										"premium": "958.00",
										"totalCreditsApplied": "0.00",
										"totalPaymentsApplied": "0.00",
										"transactions": [{
											"amountDue": "958.00",
											"description": "Invoice",
											"dueDate": "2025-04-03",
											"processingDate": "2025-03-13"
										},
          {
											"amountDue": "958.00",
											"description": "New Business",
											"dueDate": "2025-03-03",
											"processingDate": "2025-03-04"
										}]
									},
									"expirationDate": "2026-03-03",
									"insuredAccount": {
										"balance": "0.00",
										"cancellationNoticeCount": "0",
										"currentAmountDue": "0.00",
										"paymentStatus": "Current",
										"premium": "0.00",
										"totalCreditsApplied": "0.00",
										"totalPaymentsApplied": "0.00"
									},
									"lumpSumIndicator": "false",
									"paymentPlan": "Annual",
									"status": "Active",
									"version": "0"
								}
							},
							"suspenseAmount": "0.00"
						}
					}
				}
			}
		}
	}
}