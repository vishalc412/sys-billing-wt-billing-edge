# RestClient Standard Implementation

When implementing services that call other APIs, use the RestClient approach with individual configuration for scheme,
host, port, and path rather than ServiceProperties. This provides better clarity and control over endpoint
configuration.

## RestClient Implementation Example (with Custom Exception in Fallback)

```java
@Service
@Slf4j
public class InvoiceService {
   private final RestClient restClient;
   
   @Value("${api.clients.sapi-billing.scheme}")
   private String scheme;
   
   @Value("${api.clients.sapi-billing.host}")
   private String host;
   
   @Value("${api.clients.sapi-billing.port}")
   private String port;
   
   @Value("${api.clients.sapi-billing.invoicePath}")
   private String invoicePath;
   
   public InvoiceService(RestClient restClient) {
      this.restClient = restClient;
   }
   
   @CircuitBreaker(name = "getInvoice", fallbackMethod = "getInvoiceFallback")
   @Retry(name = "getInvoice", fallbackMethod = "getInvoiceFallback")
   @Observed(name = "billing.invoiceservice.getInvoice", contextualName = "Get Invoice - billing service class")
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
   
   public void getInvoiceFallback(Exception ex) {
      log.info("getInvoiceFallback method called due to: {}", ex.getMessage());
      throw new BillingException("Billing service failed", ex);
   }
}
```

## Configuration in application.yml

Configure your API clients in the application.yml file using the following structure:

```yaml
api:
  clients:
    sapi-billing:
      scheme: https
      host: api.example.com
      port: 443
      invoicePath: /api/v1/invoices
```

This pattern allows for:

- Clear separation of URI components
- Custom exception handling in fallback methods for better error propagation
- Easy configuration changes between environments
- Consistent endpoint structure across services
- Better visibility of the actual endpoint being called

## Benefits Over ServiceProperties

1. **Direct configuration**: No intermediary ServiceProperties class needed
2. **Explicit naming**: Each component of the URL is clearly named and typed
3. **Individual component updates**: Easy to update just one part of the URL (like port) without affecting others
4. **Better IDE support**: @Value annotations provide better completion and navigation in IDEs
5. **Simplified testing**: Easier to mock individual components for unit tests

