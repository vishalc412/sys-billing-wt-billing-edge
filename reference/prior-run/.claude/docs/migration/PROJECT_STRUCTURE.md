# Project Structure Documentation

## Overview

This document describes the structure of the sys-billing project and explains the organization of code, configuration,
and deployment files.

## Directory Structure

```
sys-billing/
├── src/
│   ├── main/
│   │   ├── java/com/westfield/api/billing/
│   │   │   ├── controller/          # REST endpoints
│   │   │   ├── service/             # Business logic
│   │   │   ├── mapper/              # DTO mappers (dataweave conversions)
│   │   │   ├── repository/          # Data access
│   │   │   ├── entity/              # JPA entities
│   │   │   ├── dto/                 # Data transfer objects
│   │   │   ├── config/              # Spring configuration
│   │   │   ├── exception/           # Custom exceptions & handlers
│   │   │   ├── client/              # External API clients
│   │   │   ├── filter/              # HTTP filters
│   │   │   ├── interceptor/         # HTTP interceptors
│   │   │   ├── util/                # Utility classes
│   │   │   └── SysBillingApplication.java
│   │   └── resources/
│   │       ├── application.yml
│   │       ├── application-dev.yml
│   │       ├── application-prod.yml
│   │       ├── application-test.yml
│   │       └── logback-spring.xml
│   └── test/
│       ├── java/com/westfield/api/billing/
│       │   ├── controller/
│       │   ├── service/
│       │   ├── repository/
│       │   ├── mapper/
│       │   ├── integration/
│       │   └── fixture/
│       └── resources/
│           ├── application-test.yml
│           ├── test-data.json
│           └── schema.sql
├── kustomize/
│   ├── base/
│   │   ├── deployment.yaml
│   │   ├── service.yaml
│   │   ├── secrets.yaml
│   │   ├── spring-boot-application-vars.yaml
│   │   └── kustomization.yaml
│   └── environments/
│       ├── dev/
│       ├── test/
│       ├── stage/
│       ├── perf/
│       └── prod/
├── docker-compose.yml
├── Dockerfile
├── pom.xml
└── archive/
    └── sapi-billing/                # Original Mule project reference
```

## Key Directories Explained

### controller/
- Maps to Mule HTTP listeners and flows
- Implements request routing and basic validation
- Delegates business logic to services

### service/
- Calls external APIs via client classes
- Interacts with repositories for data access
- Implements business rules and transformations

### mapper/
- Replaces Mule DataWeave transformations
- Handles complex object transformations
- Uses MapStruct for code generation

### repository/
- Replaces Mule database connectors
- Provides query methods with @Query annotations
- Handles pagination and sorting

### entity/
- Uses @Entity annotations
- Defines table structure and relationships
- Includes validation annotations

### dto/
```
dto/
├── InvoiceDTO.java
├── CustomerDTO.java
├── request/
│   ├── CreateInvoiceRequest.java
└── response/
    ├── InvoiceResponse.java
```

### config/
```
config/
├── RestTemplateConfig.java
├── SecurityConfig.java
├── JmsConfig.java
├── DataSourceConfig.java
└── AuditConfig.java
```

### exception/
```
exception/
├── BillingException.java
├── InvoiceNotFoundException.java
└── GlobalExceptionHandler.java      # @ControllerAdvice
```

### client/
- Replaces Mule HTTP request connectors
- Uses RestTemplate or WebClient
- Handles timeouts and retries

## Naming Conventions

### Java Classes

- **Controllers**: `[Entity]Controller.java`
- **Services**: `[Entity]Service.java`
- **Mappers**: `[Entity]Mapper.java`
- **Repositories**: `[Entity]Repository.java`
- **Entities**: `[Entity].java`
- **DTOs**: `[Entity]DTO.java`
- **Tests**: `[Class]Test.java`
- **Integration Tests**: `[Class]IntegrationTest.java`

### Packages

- Controllers: `com.westfield.api.billing.controller`
- Services: `com.westfield.api.billing.service`
- Mappers: `com.westfield.api.billing.mapper`
- Repositories: `com.westfield.api.billing.repository`
- Entities: `com.westfield.api.billing.entity`
- DTOs: `com.westfield.api.billing.dto`
- Configuration: `com.westfield.api.billing.config`
- Exceptions: `com.westfield.api.billing.exception`
- Clients: `com.westfield.api.billing.client`
- Utilities: `com.westfield.api.billing.util`

## File Organization Rules

1. **One class per file**: Each Java class should have its own file
2. **Package organization**: Organize by function (controller, service, etc.), not by domain
3. **Test parallel structure**: Mirror main source structure in test source
4. **Archive separation**: Keep original Mule sources in `archive/` directory

## Adding New Features

When adding new functionality to sys-billing:

1. **Create DTO** in `dto/` for request/response types
2. **Create Entity** in `entity/` if using database
3. **Create Repository** in `repository/` if data access needed
4. **Create Mapper** in `mapper/` if transformation needed
5. **Create Service** in `service/` for business logic
6. **Create Client** in `client/` if external API calls needed
7. **Create Controller** in `controller/` for HTTP endpoint
8. **Create Tests** in `src/test/java/` with same structure
9. **Update configuration** in `application.yml` if needed
10. **Update Kustomize** patches if environment-specific config

