# Request and Response Class Standards

This document outlines the standards and patterns for implementing request and response data transfer objects (DTOs) in
Westfield Spring Boot APIs.

## Package Structure

Request and response DTOs should be organized in separate packages:

```
com.westfield.api.<domain>.<context>.dtos.request
com.westfield.api.<domain>.<context>.dtos.response
```

## DTO Class Structure

### Required Annotations

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceRequest {
   // Fields and validation annotations
}
```

### Required Annotations Explanation

- `@Data` - Lombok annotation that provides getters, setters, equals, hashCode, and toString
- `@Builder` - Enables builder pattern for object creation
- `@NoArgsConstructor` - Generates a no-args constructor (required for JSON deserialization)
- `@AllArgsConstructor` - Generates a constructor with all fields as parameters

## Request Classes

### Validation Annotations

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CreateInvoiceRequest {
   
   @NotNull(message = "customerId is required")
   private String customerId;
   
   @NotNull(message = "amount is required")
   @DecimalMin("0.01")
   private BigDecimal amount;
   
   @Future(message = "dueDate must be in the future")
   private LocalDate dueDate;
   
   private String description;
}
```

### Nested Objects

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BillingAddress {
   private String street;
   private String city;
   private String state;
   private String postalCode;
   private String country;
}
```

### Enum Types

```java
public enum InvoiceStatus {
   PENDING, PAID, OVERDUE, CANCELLED;
   
   @JsonCreator
   public static InvoiceStatus fromValue(String value) {
      for (InvoiceStatus status : InvoiceStatus.values()) {
         if (status.name().equalsIgnoreCase(value)) {
            return status;
         }
      }
      throw new IllegalArgumentException("Unknown invoice status: " + value);
   }
}
```

## Response Classes

### Simple Structure

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDTO {
   private String id;
   private String customerId;
   private BigDecimal amount;
   private InvoiceStatus status;
   private LocalDate invoiceDate;
   private LocalDate dueDate;
}
```

### Null Handling

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceListResponse {
   @Builder.Default
   private List<InvoiceDTO> invoices = new ArrayList<>();
   private int totalCount;
}
```

### Serialization Annotations

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InvoiceDTO {
   private String id;
   
   @JsonInclude(JsonInclude.Include.NON_NULL)
   private String description;
   
   @JsonFormat(pattern = "yyyy-MM-dd")
   private LocalDate invoiceDate;
   
   @JsonIgnore
   private String internalNotes;
}
```

## Common Fields

### Request IDs and Tracing

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseRequest {
   @Builder.Default
   private String requestId = UUID.randomUUID().toString();
   private String correlationId;
}
```

### Timestamps

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BaseResponse {
   @Builder.Default
   private Instant timestamp = Instant.now();
   private String requestId;
}
```

## Documentation

### OpenAPI Annotations

```java
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Request object for invoice creation")
public class CreateInvoiceRequest {
   
   @Schema(description = "Customer identifier", required = true)
   @NotNull(message = "customerId is required")
   private String customerId;
   
   @Schema(description = "Invoice amount in dollars", required = true)
   @NotNull(message = "amount is required")
   private BigDecimal amount;
}
```

## DTO Mapping

### Using MapStruct

```java
@Mapper(componentModel = "spring")
public interface InvoiceMapper {
   InvoiceDTO toDTO(Invoice entity);
   Invoice toEntity(CreateInvoiceRequest request);
}
```

### Manual Mapping

```java
public InvoiceDTO mapToDTO(Invoice entity) {
   return InvoiceDTO.builder()
      .id(entity.getId())
      .customerId(entity.getCustomerId())
      .amount(entity.getAmount())
      .status(entity.getStatus())
      .build();
}
```

## Testing Requirements

### Unit Test Structure

```java
class CreateInvoiceRequestTest {
   
   private final Validator validator = Validation
      .buildDefaultValidatorFactory()
      .getValidator();
   
   @Test
   void validate_ValidRequest_NoViolations() {
      CreateInvoiceRequest request = CreateInvoiceRequest.builder()
         .customerId("CUST-001")
         .amount(BigDecimal.valueOf(100.00))
         .build();
      
      Set<ConstraintViolation<CreateInvoiceRequest>> violations = validator.validate(request);
      
      assertThat(violations).isEmpty();
   }
   
   @Test
   void validate_MissingCustomerId_HasViolations() {
      CreateInvoiceRequest request = CreateInvoiceRequest.builder()
         .amount(BigDecimal.valueOf(100.00))
         .build();
      
      Set<ConstraintViolation<CreateInvoiceRequest>> violations = validator.validate(request);
      
      assertThat(violations).hasSize(1);
      assertThat(violations.iterator().next().getMessage()).contains("customerId is required");
   }
}
```

## Security Considerations

- Never include sensitive data like passwords in response DTOs
- Use selective field serialization with JsonView or JsonFilter
- Consider using data masking for PII
- Use proper input validation to prevent injection attacks

