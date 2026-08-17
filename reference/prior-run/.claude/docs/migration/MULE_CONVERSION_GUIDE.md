# Mule to Spring Boot Conversion Guide

## Overview
This guide provides detailed instructions for converting Mule flows and services from sapi-billing into Spring Boot REST controllers and services in sys-billing.

## 1. Understanding Mule Flows vs Spring Boot

### Mule Flow Structure
```xml
<flow name="getCustomerFlow">
    <http:listener config-ref="HTTP_Listener_config" path="/customers/{id}" />
    <set-variable variableName="customerId" value="#[attributes.uriParams.id]" />
    <http:request config-ref="HTTP_Request_config" path="/api/customers/${vars.customerId}" />
    <set-payload value="#[payload]" />
</flow>
```

### Equivalent Spring Boot Code
```java
@RestController
@RequestMapping("/customers")
public class CustomerController {
    
    @GetMapping("/{id}")
    public ResponseEntity<CustomerDTO> getCustomer(@PathVariable String id) {
        String customerId = id;
        CustomerDTO customer = restTemplate.getForObject(
            "/api/customers/" + customerId, 
            CustomerDTO.class
        );
        return ResponseEntity.ok(customer);
    }
}
```

## 2. Converting Mule Listeners to Spring Controllers

### HTTP Listener to RequestMapping
```
Mule:
  config-ref="HTTP_Listener_config"
  path="/api/billing/invoice/{invoiceId}"
  
Spring:
  @RestController
  @RequestMapping("/api/billing")
  public class InvoiceController {
      @GetMapping("/invoice/{invoiceId}")
      public ResponseEntity<InvoiceDTO> getInvoice(
          @PathVariable String invoiceId) { ... }
  }
```

### HTTP Methods
| Mule | Spring |
|------|--------|
| `<http:listener ... />` (POST implied if not defined) | `@PostMapping`, `@GetMapping`, etc. |
| Mime-type: application/json | `@PostMapping(consumes="application/json")` |
| Response Mime-type: application/json | `@PostMapping(produces="application/json")` |

## 3. Converting Mule Data Transformations

### DataWeave to Java Mapper

#### Mule DataWeave Example:
```dataweave
%dw 2.0
output application/json
---
{
  customerId: payload.cust_id,
  firstName: payload.first_name,
  lastName: payload.last_name,
  email: payload.email_addr
}
```

#### Spring Boot Mapper Class:
```java
@Component
public class CustomerMapper {
    
    public CustomerDTO toDTO(CustomerEntity entity) {
        return CustomerDTO.builder()
            .customerId(entity.getCustId())
            .firstName(entity.getFirstName())
            .lastName(entity.getLastName())
            .email(entity.getEmailAddr())
            .build();
    }
    
    public CustomerEntity toEntity(CustomerDTO dto) {
        return CustomerEntity.builder()
            .custId(dto.getCustomerId())
            .firstName(dto.getFirstName())
            .lastName(dto.getLastName())
            .emailAddr(dto.getEmail())
            .build();
    }
}
```

### Using MapStruct for Complex Mappings
```java
@Mapper(componentModel = "spring")
public interface InvoiceMapper {
    
    @Mapping(source = "invoiceId", target = "inv_id")
    @Mapping(source = "invoiceDate", target = "inv_date", 
             dateFormat = "yyyy-MM-dd")
    InvoiceDTO toDTO(InvoiceEntity entity);
    
    @InheritInverseConfiguration
    InvoiceEntity toEntity(InvoiceDTO dto);
}
```

Add to pom.xml:
```xml
<dependency>
    <groupId>org.mapstruct</groupId>
    <artifactId>mapstruct</artifactId>
    <version>1.5.5.Final</version>
</dependency>
```

## 4. Converting Mule Connectors

### HTTP Request Connector
```xml
<http:request config-ref="HTTP_Request_config" 
              path="/external/api" 
              method="POST">
    <http:body>#[payload]</http:body>
</http:request>
```

**Spring Equivalent:**
```java
@Service
public class ExternalApiClient {
    
    @Autowired
    private RestTemplate restTemplate;
    
    public ResponseEntity<String> callExternalApi(String payload) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<String> request = new HttpEntity<>(payload, headers);
        return restTemplate.postForEntity(
            "http://external-api.com/external/api",
            request,
            String.class
        );
    }
}
```

### Database Connector
```xml
<db:select config-ref="Database_Config">
    <db:sql>SELECT * FROM CUSTOMERS WHERE ID = :id</db:sql>
    <db:input-parameters>#{'id': attributes.uriParams.customerId}</db:input-parameters>
</db:select>
```

**Spring Equivalent:**
```java
@Repository
public class CustomerRepository extends JpaRepository<CustomerEntity, String> {
    
    @Query(value = "SELECT * FROM CUSTOMERS WHERE ID = :id", nativeQuery = true)
    Optional<CustomerEntity> findById(@Param("id") String id);
}
```

### Message Queue Connector (WMQ)
```xml
<jms:consume config-ref="JMS_Config" destination="BILLING_QUEUE" />
```

**Spring Equivalent:**
```java
@Service
public class BillingQueueListener {
    
    @JmsListener(destination = "BILLING_QUEUE")
    public void receiveMessage(Message message) {
        String payload = message.getText();
        // Process message
    }
}
```

## 5. Converting Mule Control Flow

### Choice Router (if-else)
```xml
<choice>
    <when expression="#[payload.amount > 1000]">
        <set-payload value="#[payload ++ {priority: 'high'}]" />
    </when>
    <otherwise>
        <set-payload value="#[payload ++ {priority: 'low'}]" />
    </otherwise>
</choice>
```

**Spring Equivalent:**
```java
public Invoice processInvoice(Invoice invoice) {
    if (invoice.getAmount() > 1000) {
        invoice.setPriority("high");
    } else {
        invoice.setPriority("low");
    }
    return invoice;
}
```

### For Each Loop
```xml
<foreach collection="#[payload.items]" counterVariableName="counter">
    <set-payload value="#[payload ++ {processed: true, index: vars.counter}]" />
</foreach>
```

**Spring Equivalent:**
```java
public List<Item> processItems(List<Item> items) {
    return items.stream()
        .map((item, index) -> {
            item.setProcessed(true);
            item.setIndex(index);
            return item;
        })
        .collect(Collectors.toList());
}
```

### Try-Catch Error Handling
```xml
<try>
    <http:request ... />
    <error-handler>
        <on-error-continue type="HTTP">
            <set-payload value="#[error.description]" />
        </on-error-continue>
    </error-handler>
</try>
```

**Spring Equivalent:**
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(HttpClientErrorException.class)
    public ResponseEntity<ErrorResponse> handleHttpClientError(
            HttpClientErrorException e) {
        return ResponseEntity.status(e.getStatusCode())
            .body(new ErrorResponse(e.getMessage()));
    }
}
```

## 6. Converting Mule Global Configuration

### Global Error Handler
```xml
<error-handler>
    <on-error-propagate type="MULE:*">
        <logger level="ERROR" message="Error: #[error.description]" />
        <set-payload value="#[{error: error.description, code: error.errorType}]" />
    </on-error-propagate>
</error-handler>
```

**Spring Equivalent:**
```java
@ControllerAdvice
public class GlobalExceptionHandler {
    
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGenericException(
            Exception e, HttpServletRequest request) {
        
        log.error("Exception occurred at {}", request.getRequestURI(), e);
        
        ErrorResponse error = ErrorResponse.builder()
            .error(e.getClass().getSimpleName())
            .message(e.getMessage())
            .timestamp(LocalDateTime.now())
            .build();
        
        return ResponseEntity.status(500).body(error);
    }
}
```

## 7. Converting Mule Tests

### Spring Boot Unit Test Equivalent
```java
@SpringBootTest
@AutoConfigureMockMvc
public class CustomerControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private RestTemplate restTemplate;
    
    @Test
    public void testGetCustomer() throws Exception {
        when(restTemplate.getForObject(anyString(), eq(CustomerDTO.class)))
            .thenReturn(new CustomerDTO("123", "John", "Doe", "john@example.com"));
        
        mockMvc.perform(get("/customers/123"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value("123"))
            .andExpect(jsonPath("$.firstName").value("John"));
    }
}
```

## 8. Step-by-Step Conversion Checklist

For each Mule flow:

1. **Identify the HTTP Listener**
   - [ ] Note the path, method, and expected request/response types
   
2. **Create Spring Controller**
   - [ ] Create `@RestController` class in appropriate package
   - [ ] Add `@RequestMapping` and method-level mappings (`@GetMapping`, etc.)
   
3. **Implement Business Logic**
   - [ ] Convert Mule transformations to Java or mapper classes
   - [ ] Convert external calls to Spring `@Service` or `@Component` beans
   - [ ] Implement error handling with try-catch or `@ExceptionHandler`
   
4. **Add Tests**
   - [ ] Create unit test class in `src/test/java`
   - [ ] Add MockMvc test cases
   - [ ] Mock external dependencies
   
5. **Update Configuration**
   - [ ] Add properties to `application.yml`
   - [ ] Add environment-specific patches to `kustomize/environments/*/`
   - [ ] Update secrets in `kustomize/base/secrets.yaml`
   
6. **Validation**
   - [ ] Run `mvn clean test` - all tests pass
   - [ ] Run `mvn clean package` - jar builds successfully

## 9. Common Pitfalls and Solutions

| Issue | Mule | Spring Solution |
|-------|------|-----------------|
| State between flows | Flow variables | Use service instances or pass objects |
| Async processing | `<async>` flow | Use `@Async` or `CompletableFuture` |
| Scheduled execution | `<scheduler>` | Use `@Scheduled` annotation |
| Logging | `<logger>` | Use SLF4J logger |
| Performance | Batch processing | Use Spring Batch framework |
| Transaction handling | `<transactional>` | Use `@Transactional` on methods |
| Caching | Custom connectors | Use Spring Cache abstraction with `@Cacheable` |

