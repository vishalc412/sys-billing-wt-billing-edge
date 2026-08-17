# Controller Standards

This document outlines the standards and patterns for implementing REST controllers in Westfield Spring Boot APIs.

## Controller Class Structure

### Required Annotations

```java
@RestController
@RequestMapping("/api/v1/billing")
@Slf4j
@Validated
public class BillingController {
   // Implementation
}
```

### Required Annotations Explanation

- `@RestController` - Combines @Controller and @ResponseBody
- `@RequestMapping` - Define base path for all endpoints in the controller
- `@Slf4j` - Lombok annotation for logging
- `@Validated` - Enable method-level validation

## Endpoint Implementation Standards

### POST Endpoints

```java
@PostMapping("/invoices")
@Operation(summary = "Create invoice", description = "Creates a new billing invoice")
@ApiResponses(value = {
    @ApiResponse(responseCode = "200", description = "Invoice created successfully"),
    @ApiResponse(responseCode = "400", description = "Invalid request data"),
    @ApiResponse(responseCode = "500", description = "Internal server error")
})
public ResponseEntity<InvoiceDTO> createInvoice(@Valid @RequestBody CreateInvoiceRequest request) {
   log.info("Received create invoice request for customer: {}", request.getCustomerId());
   InvoiceDTO response = invoiceService.createInvoice(request);
   log.info("Returning created invoice with id: {}", response.getId());
   return ResponseEntity.ok(response);
}
```

### GET Endpoints

```java
@GetMapping("/{id}")
@Operation(summary = "Get invoice by ID", description = "Retrieves a specific invoice by its unique identifier")
public ResponseEntity<InvoiceDTO> getInvoice(@PathVariable @NotBlank String id) {
   log.info("Received request to get invoice with ID: {}", id);
   InvoiceDTO response = invoiceService.getInvoice(id);
   return ResponseEntity.ok(response);
}
```

## Required Dependencies

Controllers must inject services via constructor injection:

```java
private final InvoiceService invoiceService;

public BillingController(InvoiceService invoiceService) {
   this.invoiceService = invoiceService;
}
```

## Validation Standards

### Request Body Validation

- Use `@Valid` on request body parameters
- Use `@RequestBody` for POST/PUT operations
- Validation annotations should be in the DTO classes, not the controller

### Path Variable Validation

- Use appropriate validation annotations on path variables
- Example: `@PathVariable @NotBlank String id`

### Query Parameter Validation

- Use validation annotations on query parameters
- Example: `@RequestParam @Min(1) @Max(100) int limit`

## Error Handling

Controllers should NOT handle business logic exceptions directly. Let the global exception handler manage error responses.

```java
// ✅ Correct - Let service throw exceptions
public ResponseEntity<InvoiceDTO> getInvoice(@PathVariable String id) {
   InvoiceDTO response = invoiceService.getInvoice(id);
   return ResponseEntity.ok(response);
}

// ❌ Incorrect - Don't handle exceptions in controller
public ResponseEntity<?> getInvoice(@PathVariable String id) {
   try {
      InvoiceDTO response = invoiceService.getInvoice(id);
      return ResponseEntity.ok(response);
   } catch (Exception e) {
      return ResponseEntity.badRequest().body("Error occurred");
   }
}
```

## OpenAPI Documentation Standards

All public endpoints must have:

- `@Operation` with summary and description
- `@ApiResponses` with all possible HTTP status codes
- Parameter descriptions using `@Parameter` when needed

## Logging Standards

### Required Log Statements

1. **Entry logging** - Log when a request is received
2. **Exit logging** - Log when returning a response
3. **Include relevant context** - Entity types, IDs, counts, etc.

### Log Level Guidelines

- `INFO` - Normal request/response flow
- `DEBUG` - Detailed processing information
- `WARN` - Recoverable issues
- `ERROR` - Should be handled by global exception handler

## HTTP Status Code Standards

### Standard Response Codes

- `200 OK` - Successful GET, POST, PUT operations
- `201 Created` - Successful creation (rare in our APIs)
- `400 Bad Request` - Validation errors
- `404 Not Found` - Resource not found
- `500 Internal Server Error` - Unexpected errors

### ResponseEntity Usage

```java
// ✅ Correct
return ResponseEntity.ok(response);

// ✅ Also correct for specific status codes
return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);

// ❌ Avoid - Let global exception handler manage error responses
return ResponseEntity.badRequest().body("Error message");
```

## Security Considerations

- All endpoints are secured by default through Spring Security configuration
- No additional security annotations needed at controller level
- Sensitive data should not be logged (PII, credentials, etc.)

## Testing Requirements

Each controller method must have corresponding test cases covering:

- Happy path scenarios
- Validation error scenarios
- Edge cases specific to the business logic

Example test structure:

```java
@WebMvcTest(BillingController.class)
class BillingControllerTest {
   
   @MockBean
   private InvoiceService invoiceService;
   
   @Test
   void getInvoice_ValidId_ReturnsSuccess() {
      // Test implementation
   }
   
   @Test
   void getInvoice_InvalidId_ReturnsBadRequest() {
      // Test implementation
   }
}
```

