# Configuration Mapping: Mule Properties to Spring Boot YAML

## Overview
This guide maps Mule application properties and configurations to Spring Boot YAML format and Kustomize patches.

## 1. Basic Configuration Structure

### Mule Properties File Structure
```properties
# mule-app.properties
# Server Configuration
server.port=8080
server.context.path=/api

# Database
database.host=localhost
database.port=5432
database.username=user
database.password=pass
database.url=jdbc:postgresql://localhost:5432/billing

# External APIs
api.customer.url=http://customer-api.com
api.billing.url=http://billing-api.com
api.timeout=5000
api.max.retries=3

# Security
security.enabled=true
security.oauth.provider=http://auth-server.com
security.oauth.client-id=client123
security.oauth.client-secret=${secure::oauth.secret}

# Logging
logging.level=INFO
logging.file=/var/log/billing.log

# Message Queue
mq.broker.host=mq-server.com
mq.broker.port=1414
mq.queue.name=BILLING_QUEUE
```

### Spring Boot YAML Equivalent (application.yml)
```yaml
server:
  port: 8080
  servlet:
    context-path: /api

spring:
  datasource:
    url: jdbc:postgresql://localhost:5432/billing
    username: ${DB_USERNAME:user}
    password: ${DB_PASSWORD:pass}
    driver-class-name: org.postgresql.Driver
  
  jpa:
    hibernate:
      ddl-auto: validate
    show-sql: false
    properties:
      hibernate:
        format_sql: true
  
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: http://auth-server.com
  
  jms:
    broker-url: tcp://mq-server.com:1414
    queue-manager: QM_BILLING
  
  logging:
    level:
      root: INFO
      com.westfield.api: DEBUG
    file:
      name: /var/log/billing.log

app:
  api:
    customer:
      url: http://customer-api.com
      timeout: 5000
      max-retries: 3
    billing:
      url: http://billing-api.com
      timeout: 5000
      max-retries: 3
  
  security:
    enabled: true
    oauth:
      provider: http://auth-server.com
      client-id: ${OAUTH_CLIENT_ID:client123}
      client-secret: ${OAUTH_CLIENT_SECRET}
```

## 2. Environment-Specific Configuration

### Mule Environment Properties
```properties
# mule-app-dev.properties
database.host=dev-db.westfield.local
api.customer.url=http://dev-customer-api.westfield.local

# mule-app-prod.properties  
database.host=prod-db.westfield.corp
api.customer.url=http://prod-customer-api.westfield.corp
```

### Spring Boot with Profiles (application-{profile}.yml)
```yaml
# src/main/resources/application-dev.yml
spring:
  datasource:
    url: jdbc:postgresql://dev-db.westfield.local/billing

app:
  api:
    customer:
      url: http://dev-customer-api.westfield.local
```

```yaml
# src/main/resources/application-prod.yml
spring:
  datasource:
    url: jdbc:postgresql://prod-db.westfield.corp/billing

app:
  api:
    customer:
      url: http://prod-customer-api.westfield.corp
```

### Kustomize Environment Patches
```yaml
# kustomize/environments/dev/spring-boot-application-vars.patch.yaml
- op: replace
  path: /spec/template/spec/containers/0/env/0
  value:
    name: SPRING_PROFILES_ACTIVE
    value: dev
- op: add
  path: /spec/template/spec/containers/0/env/-
  value:
    name: DB_USERNAME
    valueFrom:
      secretKeyRef:
        name: billing-secrets
        key: db-username
```

```yaml
# kustomize/environments/prod/spring-boot-application-vars.patch.yaml
- op: replace
  path: /spec/template/spec/containers/0/env/0
  value:
    name: SPRING_PROFILES_ACTIVE
    value: prod
- op: add
  path: /spec/template/spec/containers/0/env/-
  value:
    name: DB_USERNAME
    valueFrom:
      secretKeyRef:
        name: billing-secrets
        key: db-username-prod
```

## 3. Common Mule to Spring Mapping Table

| Mule Configuration | Spring Boot Property | Kustomize Patch | Type | Notes |
|-------------------|---------------------|-----------------|------|-------|
| `mule.env` | `spring.profiles.active` | Environment Variable | String | Controls active profile |
| Database properties | `spring.datasource.*` | Secrets | Connection | Use kustomize overlays per env |
| API endpoints | `app.api.*` | ConfigMap | String | Can be configurable |
| Security keys | `spring.security.oauth2.*` | Secrets | Sensitive | Never in application.yml |
| Logging level | `logging.level.*` | Environment Variable | String | Override at runtime |
| Message broker | `spring.jms.*` | Secrets | Connection | Connection string in secret |
| Feature flags | `app.features.*` | ConfigMap | Boolean | Feature toggles |
| Timeouts | `app.api.*.timeout` | ConfigMap | Integer | Performance tuning |

## 4. Database Configuration

### Mule Database Configuration
```xml
<db:config name="Database_Config">
    <db:generic-connection url="${database.url}" 
                           driverClassName="org.postgresql.Driver"
                           user="${database.username}"
                           password="${database.password}" />
</db:config>
```

### Spring Boot Equivalent
```yaml
spring:
  datasource:
    url: ${DATABASE_URL:jdbc:postgresql://localhost:5432/billing}
    username: ${DATABASE_USERNAME}
    password: ${DATABASE_PASSWORD}
    driver-class-name: org.postgresql.Driver
    hikari:
      maximum-pool-size: 10
      minimum-idle: 2
      connection-timeout: 30000
  
  jpa:
    database-platform: org.hibernate.dialect.PostgreSQL10Dialect
    hibernate:
      ddl-auto: validate
    properties:
      hibernate:
        jdbc:
          batch_size: 20
        fetch_size: 100
```

### Kustomize Secret Configuration
```yaml
# kustomize/base/secrets.yaml
apiVersion: v1
kind: Secret
metadata:
  name: billing-secrets
type: Opaque
stringData:
  database-url: "jdbc:postgresql://db-host:5432/billing"
  database-username: "{{ DB_USERNAME }}"
  database-password: "{{ DB_PASSWORD }}"
  
---

# kustomize/environments/prod/secrets.patch.yaml
- op: replace
  path: /stringData/database-url
  value: "jdbc:postgresql://prod-db.westfield.corp:5432/billing"
- op: replace
  path: /stringData/database-username
  value: "prod_user"
```

## 5. Security and OAuth2 Configuration

### Mule Security Configuration
```xml
<http:listener-config name="HTTP_Listener_config" host="0.0.0.0" port="8080">
    <http:security>
        <http:tls-server-credentials>
            <http:keystore path="keystore.jks" password="${secure::keystore.password}" />
        </http:tls-server-credentials>
    </http:security>
</http:listener-config>
```

### Spring Boot Equivalent
```yaml
server:
  ssl:
    key-store: file:keystore.jks
    key-store-password: ${SSL_KEYSTORE_PASSWORD}
    key-store-type: JKS
    key-alias: billing-key

spring:
  security:
    oauth2:
      resourceserver:
        jwt:
          issuer-uri: ${OAUTH_ISSUER_URI:http://auth-server.com}
          jwk-set-uri: ${OAUTH_JWK_SET_URI:http://auth-server.com/.well-known/jwks.json}
      client:
        registration:
          custom:
            client-id: ${OAUTH_CLIENT_ID}
            client-secret: ${OAUTH_CLIENT_SECRET}
            authorization-grant-type: client_credentials
        provider:
          custom:
            token-uri: ${OAUTH_TOKEN_URI:http://auth-server.com/oauth/token}
```

## 6. Logging Configuration

### Mule Logging
```properties
log4j.rootLogger=INFO, file, console
log4j.logger.com.westfieldgrp=DEBUG
log4j.appender.file=org.apache.log4j.RollingFileAppender
log4j.appender.file.File=/var/log/billing.log
log4j.appender.file.MaxFileSize=10MB
log4j.appender.file.MaxBackupIndex=10
```

### Spring Boot Logging (application.yml)
```yaml
logging:
  level:
    root: INFO
    com.westfield.api: DEBUG
    org.springframework: WARN
    org.springframework.security: DEBUG
  pattern:
    console: "%d{yyyy-MM-dd HH:mm:ss} - %msg%n"
    file: "%d{yyyy-MM-dd HH:mm:ss} [%thread] %-5level %logger{36} - %msg%n"
  file:
    name: /var/log/billing.log
    max-size: 10MB
    max-history: 10
    total-size-cap: 100MB
```

## 7. Message Queue Configuration

### Mule WMQ Configuration
```xml
<jms:config name="JMS_Config">
    <jms:wmq-connection host="${mq.broker.host}" 
                        port="${mq.broker.port}"
                        queue-manager="QM_BILLING">
        <jms:client-id-prefix>sapi-billing</jms:client-id-prefix>
    </jms:wmq-connection>
</jms:config>
```

### Spring Boot JMS Configuration
```yaml
spring:
  jms:
    broker-url: tcp://${MQ_BROKER_HOST:localhost}:${MQ_BROKER_PORT:1414}
    user: ${MQ_USERNAME}
    password: ${MQ_PASSWORD}
    listener:
      acknowledge-mode: auto
      concurrency: "1-10"
```

### Spring JMS Listener
```java
@Component
public class BillingMessageListener {
    
    @JmsListener(destination = "BILLING_QUEUE", concurrency = "5-10")
    public void onMessage(Message message) {
        try {
            String payload = message.getText();
            // Process billing message
        } catch (JMSException e) {
            log.error("Failed to process JMS message", e);
        }
    }
}
```

## 8. Property Validation and Default Values

### Use Spring `@ConfigurationProperties`
```java
@Configuration
@ConfigurationProperties(prefix = "app.api")
@Validated
public class ApiProperties {
    
    @NotBlank
    private String customerUrl;
    
    @NotBlank
    private String billingUrl;
    
    @Min(1000)
    @Max(60000)
    private Integer timeout = 30000;
    
    @Min(1)
    @Max(5)
    private Integer maxRetries = 3;
    
    // getters and setters
}
```

## 9. Kustomize Configuration Organization

### Base Configuration (shared across all environments)
```yaml
# kustomize/base/spring-boot-application-vars.yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: billing-config
data:
  application.yml: |
    server:
      port: 8080
    app:
      api:
        timeout: 30000
        max-retries: 3
```

### Environment-Specific Patches
```yaml
# kustomize/environments/dev/spring-boot-application-vars.patch.yaml
- op: add
  path: /data/application-override.yml
  value: |
    logging:
      level:
        com.westfield.api: DEBUG
    app:
      api:
        customer-url: http://dev-customer-api.local
```

## 10. Conversion Checklist

- [ ] Extract all properties from Mule configuration files
- [ ] Create application.yml with base configuration
- [ ] Create application-{env}.yml for each environment
- [ ] Create kustomize patches for environment-specific overrides
- [ ] Migrate security configuration to Spring Security
- [ ] Configure database connection pooling
- [ ] Set up logging with appropriate levels per environment
- [ ] Add configuration validation with `@ConfigurationProperties`
- [ ] Test configuration loading with `mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"`
- [ ] Verify all required properties are defined in kustomize secrets

