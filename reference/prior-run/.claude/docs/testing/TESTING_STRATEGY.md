# Testing Strategy and Guidelines

## Overview
This document outlines the testing strategy for the sys-billing project, including unit tests, integration tests, and testing best practices.

## Testing Pyramid

```
        /\
       /  \           E2E/Manual Tests (5%)
      /----\          
     /      \         Integration Tests (25%)
    /--------\
   /          \       Unit Tests (70%)
  /------------\
```

## Testing Levels

### 1. Unit Tests (70% of tests)

#### Purpose
- Test individual components in isolation
- Mock all external dependencies
- Fast execution (< 1ms per test typically)
- Verify business logic correctness

#### Tools
- **JUnit 5**: Testing framework
- **Mockito**: Mocking framework
- **AssertJ**: Fluent assertions

#### Controller Unit Tests

```java
@SpringBootTest
@AutoConfigureMockMvc
public class InvoiceControllerTest {
    
    @Autowired
    private MockMvc mockMvc;
    
    @MockBean
    private InvoiceService invoiceService;
    
    @Test
    public void testGetInvoiceSuccess() throws Exception {
        String invoiceId = "INV-001";
        InvoiceDTO mockInvoice = InvoiceDTO.builder()
            .id(invoiceId)
            .amount(BigDecimal.valueOf(1000.00))
            .status("PAID")
            .build();
        
        when(invoiceService.getInvoice(invoiceId)).thenReturn(mockInvoice);
        
        mockMvc.perform(get("/invoices/{id}", invoiceId)
            .accept(MediaType.APPLICATION_JSON))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.id").value(invoiceId))
            .andExpect(jsonPath("$.amount").value(1000.00))
            .andExpect(jsonPath("$.status").value("PAID"));
        
        verify(invoiceService).getInvoice(invoiceId);
    }
    
    @Test
    public void testGetInvoiceNotFound() throws Exception {
        String invoiceId = "INV-999";
        when(invoiceService.getInvoice(invoiceId))
            .thenThrow(new InvoiceNotFoundException("Invoice not found"));
        
        mockMvc.perform(get("/invoices/{id}", invoiceId))
            .andExpect(status().isNotFound());
    }
}
```

#### Service Unit Tests

```java
public class InvoiceServiceTest {
    
    @InjectMocks
    private InvoiceService invoiceService;
    
    @Mock
    private InvoiceRepository invoiceRepository;
    
    @Mock
    private InvoiceMapper invoiceMapper;
    
    @BeforeEach
    public void setUp() {
        MockitoAnnotations.openMocks(this);
    }
    
    @Test
    public void testGetInvoiceSuccess() {
        String invoiceId = "INV-001";
        Invoice entity = Invoice.builder()
            .id(invoiceId)
            .amount(BigDecimal.valueOf(1000.00))
            .status("PAID")
            .build();
        
        InvoiceDTO dto = InvoiceDTO.builder()
            .id(invoiceId)
            .amount(BigDecimal.valueOf(1000.00))
            .status("PAID")
            .build();
        
        when(invoiceRepository.findById(invoiceId)).thenReturn(Optional.of(entity));
        when(invoiceMapper.toDTO(entity)).thenReturn(dto);
        
        InvoiceDTO result = invoiceService.getInvoice(invoiceId);
        
        assertThat(result).isNotNull().usingRecursiveComparison().isEqualTo(dto);
        verify(invoiceRepository).findById(invoiceId);
        verify(invoiceMapper).toDTO(entity);
    }
}
```

### 2. Integration Tests (25% of tests)

#### Purpose
- Test components working together
- Use real database or TestContainers
- Test external service interactions

#### Repository Integration Tests

```java
@SpringBootTest
@Testcontainers
public class InvoiceRepositoryIntegrationTest {
    
    @Container
    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>()
        .withDatabaseName("billing_test")
        .withUsername("test")
        .withPassword("test");
    
    @Autowired
    private InvoiceRepository invoiceRepository;
    
    @DynamicPropertySource
    static void configureProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }
    
    @Test
    public void testSaveAndRetrieveInvoice() {
        Invoice invoice = Invoice.builder()
            .customerId("CUST-001")
            .amount(BigDecimal.valueOf(1000.00))
            .status("PENDING")
            .build();
        
        Invoice saved = invoiceRepository.save(invoice);
        Optional<Invoice> retrieved = invoiceRepository.findById(saved.getId());
        
        assertThat(retrieved).isPresent().get()
            .usingRecursiveComparison().isEqualTo(saved);
    }
}
```

### 3. End-to-End Tests (5% of tests)

#### Purpose
- Test complete user workflows
- May involve actual external services (staging/test environments)
- Long running, run less frequently

#### E2E Test Example

```java
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
public class BillingWorkflowE2ETest {
    
    @Autowired
    private TestRestTemplate restTemplate;
    
    @Test
    public void testCompleteInvoicingWorkflow() {
        CreateInvoiceRequest invoiceRequest = CreateInvoiceRequest.builder()
            .customerId("CUST-001")
            .amount(BigDecimal.valueOf(1000.00))
            .dueDate(LocalDate.now().plusMonths(1))
            .build();
        
        ResponseEntity<InvoiceDTO> invoiceResponse = restTemplate.postForEntity(
            "/invoices", invoiceRequest, InvoiceDTO.class
        );
        
        assertThat(invoiceResponse.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        String invoiceId = invoiceResponse.getBody().getId();
        
        ResponseEntity<InvoiceDTO> getResponse = restTemplate.getForEntity(
            "/invoices/{id}", InvoiceDTO.class, invoiceId
        );
        
        assertThat(getResponse.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(getResponse.getBody().getStatus()).isEqualTo("PENDING");
    }
}
```

## Testing Configuration

### pom.xml Dependencies

```xml
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-test</artifactId>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>postgresql</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
<dependency>
    <groupId>org.testcontainers</groupId>
    <artifactId>junit-jupiter</artifactId>
    <version>1.19.3</version>
    <scope>test</scope>
</dependency>
```

### application-test.yml

```yaml
spring:
  datasource:
    url: jdbc:h2:mem:testdb
    driver-class-name: org.h2.Driver
    username: sa
    password:
  jpa:
    hibernate:
      ddl-auto: create-drop
    database-platform: org.hibernate.dialect.H2Dialect

logging:
  level:
    root: WARN
    com.westfield.api: DEBUG
```

## Test Data Management

### Using Test Fixtures/Builders

```java
public class InvoiceBuilder {
    
    private String id = UUID.randomUUID().toString();
    private String customerId = "CUST-DEFAULT";
    private BigDecimal amount = BigDecimal.valueOf(1000.00);
    private String status = "PENDING";
    
    public InvoiceBuilder withCustomerId(String customerId) {
        this.customerId = customerId;
        return this;
    }
    
    public InvoiceBuilder withAmount(BigDecimal amount) {
        this.amount = amount;
        return this;
    }
    
    public Invoice build() {
        return Invoice.builder()
            .id(id)
            .customerId(customerId)
            .amount(amount)
            .status(status)
            .build();
    }
}
```

## Running Tests

### Maven Commands

```bash
# Run all tests
mvn clean test

# Run specific test class
mvn test -Dtest=InvoiceControllerTest

# Run specific test method
mvn test -Dtest=InvoiceControllerTest#testGetInvoiceSuccess

# Run with coverage
mvn clean test jacoco:report

# Skip tests during build
mvn clean package -DskipTests

# Run integration tests only
mvn verify -P integration-tests
```

## Testing Best Practices

1. **Test Naming**: Use descriptive names like `testGetInvoiceSuccess`, `testGetInvoiceNotFound`
2. **AAA Pattern**: Arrange, Act, Assert - structure all tests this way
3. **One Assertion Focus**: Each test should focus on one behavior
4. **Mock External Dependencies**: Never call real external services in unit tests
5. **Use Builders**: Create test data with builders for readability
6. **Test Edge Cases**: Include tests for null, empty, boundary conditions
7. **Keep Tests Fast**: Unit tests should complete in milliseconds
8. **Isolate Tests**: No dependencies between tests, run in any order
9. **Use Fixtures**: Reuse common test setup in @BeforeEach methods
10. **Test Error Paths**: Don't just test the happy path

## Test Coverage Goals

- **Overall Coverage**: Minimum 80%
- **Controllers**: 70-80%
- **Services**: 85-95% (business logic critical)
- **Repositories**: 50-70%
- **Mappers**: 80-90%
- **Utilities**: 70-80%
- **Exceptions/Config**: 60-70%

