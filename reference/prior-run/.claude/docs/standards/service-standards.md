# Service Class Standards

This document outlines the standards and patterns for implementing service classes in Westfield Spring Boot APIs.

## Service Class Structure

### Required Annotations

```java
@Service
@Slf4j
@Observed(name = "domain.feature.service", contextualName = "Domain Feature Service")
public class FeatureService {
   // Implementation
}
```

### Required Annotations Explanation

- `@Service` - Marks the class as a Spring service component
- `@Slf4j` - Lombok annotation for logging support
- `@Observed` - Enables OpenTelemetry observability for monitoring and tracing

## Constructor Injection Pattern

Services must use constructor injection for all dependencies:

```java
@Service
@Slf4j
@Observed(name = "domain.feature.service", contextualName = "Domain Feature Service")
public class FeatureService {
   private final FeatureRepository featureRepository;
   private final RestClient restClient;
   
   public FeatureService(FeatureRepository featureRepository, RestClient restClient) {
      this.featureRepository = featureRepository;
      this.restClient = restClient;
   }
}
```

## RestClient Implementation Standards

### Configuration Properties

Use individual @Value annotations for endpoint configuration:

```java
@Value("${api.clients.sapi-billing.scheme}")
private String scheme;

@Value("${api.clients.sapi-billing.host}")
private String host;

@Value("${api.clients.sapi-billing.port}")
private String port;

@Value("${api.clients.sapi-billing.invoicePath}")
private String invoicePath;
```

### RestClient Usage Pattern

```java
public ResponseEntity<InvoiceResponse> getInvoice(InvoiceRequest request) {
   log.info("Request sent to sys-billing");
   
   ResponseEntity<InvoiceResponse> response = restClient
      .post()
      .uri(uriBuilder -> uriBuilder
         .scheme(scheme)
         .host(host)
         .port(port)
         .path(invoicePath)
         .build())
      .body(request)
      .retrieve()
      .toEntity(InvoiceResponse.class);
   
   log.info("Response received from sys-billing /invoices");
   
   return response;
}
```

## Resilience Patterns

### Required Resilience Annotations

All external service calls must include:

```java
@CircuitBreaker(name = "getInvoice", fallbackMethod = "getInvoiceFallback")
@Retry(name = "getInvoice", fallbackMethod = "getInvoiceFallback")
@Observed(name = "billing.invoiceservice.getInvoice", contextualName = "Get Invoice - billing service class")
public ResponseEntity<InvoiceResponse> getInvoice(InvoiceRequest request) {
   // Implementation
}
```

### Fallback Method Implementation

```java
public void getInvoiceFallback(Exception ex) {
   log.info("getInvoiceFallback method called due to: {}", ex.getMessage());
   throw new BillingException("Billing service failed", ex);
}
```

### Fallback Method Rules

1. **Same return type** - Fallback method must match the original method's return type or be void
2. **Same parameters** - Must accept the same parameters plus an Exception parameter
3. **Throw custom exceptions** - Always throw a custom business exception, not generic exceptions
4. **Log the fallback** - Always log when fallback is triggered

## Business Logic Implementation

### Method Structure

```java
public InvoiceResponse processInvoice(InvoiceRequest request) {
   log.info("Processing invoice for customer: {}", request.getCustomerId());
   
   // 1. Input validation (if not handled by validation annotations)
   validateRequest(request);
   
   // 2. Business logic processing
   InvoiceResponse response = performInvoiceProcessing(request);
   
   // 3. Response processing/transformation
   processResponse(response);
   
   log.info("Completed invoice processing for id: {}", response.getId());
   
   return response;
}
```

## Exception Handling

### Custom Exception Usage

```java
// ✅ Correct
throw new BillingException("Unable to process invoice request", ex);
throw new ValidationException("Invalid customer ID provided");

// ❌ Incorrect
throw new RuntimeException("Error occurred");
throw new Exception("Something went wrong");
```

### Exception Hierarchy

```java
public class BillingException extends RuntimeException {
   public BillingException(String message) {
      super(message);
   }
   
   public BillingException(String message, Throwable cause) {
      super(message, cause);
   }
}
```

## Logging Standards

### Required Log Statements

1. **Method entry** - Log when processing begins
2. **External calls** - Log before and after external service calls
3. **Method exit** - Log when processing completes
4. **Error conditions** - Log errors with context

### Logging Best Practices

- Include relevant business context in log messages
- Use structured logging with key-value pairs when possible
- Don't log sensitive information (PII, credentials, etc.)
- Use appropriate log levels (INFO, DEBUG, WARN, ERROR)

## Data Transformation

### Request/Response Mapping

```java
private ExternalInvoiceRequest mapToExternalRequest(InvoiceRequest request) {
   return ExternalInvoiceRequest.builder()
      .customerId(request.getCustomerId())
      .amount(request.getAmount())
      .dueDate(request.getDueDate())
      .build();
}
```

## Configuration Management

### Application Properties Structure

```yaml
api:
  clients:
    sapi-billing:
      scheme: https
      host: api.example.com
      port: 443
      invoicePath: /api/v1/invoices
```

### Property Validation

```java
@PostConstruct
public void validateConfiguration() {
   if (host == null || host.trim().isEmpty()) {
      throw new IllegalStateException("Billing service host must be configured");
   }
}
```

## Testing Requirements

### Unit Test Structure

```java
@ExtendWith(MockitoExtension.class)
class InvoiceServiceTest {
   
   @Mock
   private RestClient restClient;
   
   @InjectMocks
   private InvoiceService invoiceService;
   
   @Test
   void getInvoice_ValidRequest_ReturnsResponse() {
      // Given
      InvoiceRequest request = createValidRequest();
      InvoiceResponse expectedResponse = createExpectedResponse();
      
      // When
      InvoiceResponse response = invoiceService.getInvoice(request);
      
      // Then
      assertThat(response).isNotNull();
   }
   
   @Test
   void getInvoice_ServiceFailure_ThrowsCustomException() {
      // Given
      InvoiceRequest request = createValidRequest();
      when(restClient.post()).thenThrow(new RestClientException("Service unavailable"));
      
      // When/Then
      assertThatThrownBy(() -> invoiceService.getInvoice(request))
         .isInstanceOf(BillingException.class)
         .hasMessageContaining("Billing service failed");
   }
}
```

## Scatter-Gather Pattern

For parallel service calls, implement the Scatter-Gather pattern:

```java
public List<Object> scatterGather(RequestA requestA, RequestB requestB)
   throws ExecutionException, InterruptedException {
   CompletableFuture<List<ResponseA>> futureA = callServiceAAsync(requestA);
   CompletableFuture<List<ResponseB>> futureB = callServiceBAsync(requestB);
   
   CompletableFuture.allOf(futureA, futureB).join();
   
   List<Object> merged = new ArrayList<>();
   merged.addAll(futureA.get());
   merged.addAll(futureB.get());
   
   return merged;
}

@Async
@CircuitBreaker(name = "serviceA", fallbackMethod = "serviceAFallback")
@Retry(name = "serviceA", fallbackMethod = "serviceAFallback")
public CompletableFuture<List<ResponseA>> callServiceAAsync(RequestA request) {
   return CompletableFuture.completedFuture(callServiceA(request));
}
```

