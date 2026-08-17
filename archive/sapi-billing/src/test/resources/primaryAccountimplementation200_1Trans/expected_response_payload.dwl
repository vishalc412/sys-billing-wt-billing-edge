%dw 2.0
import * from dw::test::Asserts
---
payload must equalTo(
{
	"accountBalance": 2540.81,
 	"accountNumber": "6000077687",
 	"billTocontact":{
		"name":"2941_Repro",
		"address":{
			"street":"1200 East St",
			"city":"Tiskilwa",
			"state":"IL",
			"zip":"61368"
		}
	},
	"billingSystemCode": "DuckCreek",
	"billType": "PA",
	"dueAmount": null,
	"dueDate": null,
	"dateBilled": "2024-09-17",
	"lastPaymentAmount": null,
	"lastPaymentDate": null,
	"eft": "false",
	"pastDueAmount": 212.74,
	"pastDueDate": "2024-10-07",
	"transactions": [
		{
			"amountDue": 212.74,
            "description": "Invoice",
            "dueDate": "2024-10-07",
            "processingDate": "2024-09-17"
        }
    ]
})