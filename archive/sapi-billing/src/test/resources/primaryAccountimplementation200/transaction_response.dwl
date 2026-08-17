%dw 2.0
import * from dw::test::Asserts
---
payload must equalTo(
[
  {
    amountDue: 212.74,
    description: "Invoice",
    dueDate: "2024-10-07",
    processingDate: "2024-09-17"
  }, 
  {
    amountDue: 54.00,
    description: "Endorsement",
    dueDate: "2025-01-07",
    processingDate: "2024-07-24"
  }, 
  {
    amountDue: 2480.81,
    description: "New Business",
    dueDate: "2024-10-07",
    processingDate: "2024-07-24"
  }, 
  {
    amountDue: 1000.00,
    description: "Payment Received",
    dueDate: "2024-10-07",
    processingDate: "2024-07-24"
  }, 
  {
    amountDue: 0.00,
    description: "Account Created",
    dueDate: "2024-07-24",
    processingDate: "2024-07-24"
  }
]
)