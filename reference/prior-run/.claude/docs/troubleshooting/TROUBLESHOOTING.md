# Troubleshooting Guide

## Overview

This document provides solutions for common issues encountered during the Mule to Spring Boot conversion process and
while running the sys-billing application.

## Build Issues

### Issue: Maven Build Fails with "Cannot find symbol"

**Symptoms:**
```
[ERROR] error: cannot find symbol: class InvoiceDTO
[ERROR]   location: class com.westfield.api.billing.service.InvoiceService
```

**Solutions:**
1. Check that the DTO class exists in `src/main/java/com/westfield/api/billing/dto/`
2. Add the correct import statement to the file
3. Verify package names match (use IDE's "Organize Imports" feature)
4. Run `mvn clean` to clear any stale compiled classes
5. Rebuild with `mvn clean compile`

---

### Issue: Java Compilation Error - "Target release 21 not supported"

**Symptoms:**
```
[ERROR] Fatal error compiling: invalid target release: 21 ...
```

**Solutions:**
1. Verify Java 21 is installed: `java -version`
2. Set JAVA_HOME to Java 21:
   ```powershell
   # Windows PowerShell
   $env:JAVA_HOME = "C:\Program Files\Java\jdk-21"
   ```
3. Check pom.xml has correct Java version:
   ```xml
   <properties>
       <java.version>21</java.version>
   </properties>
   ```

---

### Issue: Dependency Conflicts or Version Mismatch

**Solutions:**
1. View dependency tree: `mvn dependency:tree`
2. Exclude conflicting transitive dependencies:
   ```xml
   <dependency>
       <groupId>com.example</groupId>
       <artifactId>some-library</artifactId>
       <exclusions>
           <exclusion>
               <groupId>conflicting-group</groupId>
               <artifactId>conflicting-artifact</artifactId>
           </exclusion>
       </exclusions>
   </dependency>
   ```

---

## Test Failures

### Issue: Tests Fail with "No suitable driver found"

**Symptoms:**
```
java.sql.SQLException: No suitable driver found for jdbc:postgresql://localhost:5432/billing
```

**Solutions:**
1. Ensure PostgreSQL driver is in pom.xml:
   ```xml
   <dependency>
       <groupId>org.postgresql</groupId>
       <artifactId>postgresql</artifactId>
       <scope>runtime</scope>
   </dependency>
   ```
2. For unit tests, use H2 in-memory database and verify `application-test.yml`

---

### Issue: "MockMvc request did not produce a valid mapping"

**Symptoms:**
```
java.lang.AssertionError: Status expected:<200> but was:<404>
```

**Solutions:**
1. Verify controller has `@RestController` annotation
2. Verify test has `@SpringBootTest` and `@AutoConfigureMockMvc`
3. Match request path with controller mapping - include base path

---

### Issue: Test Passes Locally but Fails in CI/CD

**Solutions:**
1. Use TestContainers for integration tests:
   ```java
   @Testcontainers
   public class InvoiceRepositoryIntegrationTest {
       @Container
       static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>();
   ```
2. Use `@DynamicPropertySource` for dynamic test configuration
3. Don't hardcode localhost URLs in tests

---

## Runtime Issues

### Issue: Application Fails to Start - "Port Already in Use"

**Symptoms:**
```
Caused by: java.net.BindException: Address already in use
```

**Solutions (Windows PowerShell):**
```powershell
# Find PID on port 8080
netstat -ano | findstr :8080
# Kill the process
taskkill /PID <PID> /F
```

Or use different port:
```bash
mvn spring-boot:run -Dspring-boot.run.arguments="--server.port=8081"
```

---

### Issue: Database Connection Fails at Runtime

**Symptoms:**
```
org.postgresql.util.PSQLException: Connection to localhost:5432 refused.
```

**Solutions:**
1. Check PostgreSQL is running: `docker ps | grep postgres`
2. Verify connection parameters in `application.yml`
3. Start database with docker-compose: `docker-compose up -d`

---

### Issue: "Invalid JWT Token" or Authentication Failures

**Solutions:**
1. Verify OAuth2 configuration in application.yml:
   ```yaml
   spring:
     security:
       oauth2:
         resourceserver:
           jwt:
             issuer-uri: ${OAUTH_ISSUER_URI}
             jwk-set-uri: ${OAUTH_JWK_SET_URI}
   ```
2. For development, disable security temporarily (NOT for production):
   ```java
   http.authorizeRequests().anyRequest().permitAll();
   ```

---

### Issue: Timeouts When Calling External APIs

**Solutions:**
1. Increase timeout in application.yml:
   ```yaml
   app:
     api:
       timeout: 60000
   ```
2. Implement retry logic with `@Retryable`
3. Configure circuit breaker with Resilience4j

---

### Issue: "No bean named 'X' available"

**Solutions:**
1. Add `@Service` / `@Component` / `@Repository` annotation to the class
2. Verify component scan includes the package:
   ```java
   @SpringBootApplication
   @ComponentScan(basePackages = "com.westfield.api.billing")
   public class BillingApplication {
   ```

---

## Conversion-Specific Issues

### Issue: Cannot Map Mule Flow to Spring Controller

**Solutions:**
1. Create manual conversion task in `sys-billing/manual/<name>.todo.md`
2. Break complex logic into smaller services using `CompletableFuture`

---

### Issue: Mule Property Placeholder Cannot Be Resolved

**Symptoms:**
```
Could not resolve placeholder 'api.endpoint.url' in value "${api.endpoint.url}"
```

**Solutions:**
1. Add property to `application.yml`:
   ```yaml
   app:
     api:
       endpoint:
         url: http://api.example.com
   ```
2. Use Spring `@Value` with default:
   ```java
   @Value("${app.api.endpoint.url:http://localhost:8080}")
   private String apiEndpointUrl;
   ```

---

## Docker and Kustomize Issues

### Issue: Docker Build Fails

**Solutions:**
1. Ensure jar is built first:
   ```bash
   mvn clean package
   docker build -t sys-billing:latest .
   ```
2. Check Dockerfile:
   ```dockerfile
   FROM openjdk:21-slim
   WORKDIR /app
   COPY target/billing-0.0.1-SNAPSHOT.jar app.jar
   ENTRYPOINT ["java", "-jar", "app.jar"]
   ```

---

### Issue: Kustomize Deployment Fails in Kubernetes

**Solutions:**
1. Validate kustomize configuration:
   ```bash
   kubectl kustomize kustomize/base
   kubectl kustomize kustomize/environments/dev
   ```
2. Verify secrets exist before deployment:
   ```bash
   kubectl get secrets
   ```

---

## Performance Issues

### Issue: Application Runs Slowly

**Solutions:**
1. Enable SQL logging to find N+1 queries:
   ```yaml
   logging:
     level:
       org.hibernate.SQL: DEBUG
   ```
2. Add database indexes via `@Index` on `@Table`
3. Use `@Async` for long-running operations
4. Enable caching with `@Cacheable`

---

## Quick Diagnostic Checklist

When experiencing issues:

- [ ] **Build**: `mvn clean compile` - any compilation errors?
- [ ] **Tests**: `mvn clean test` - do tests pass?
- [ ] **Jar**: `mvn clean package` - does jar build?
- [ ] **Logs**: Check application logs in `target/` or stdout
- [ ] **Database**: Is database running? Can you connect with a client tool?
- [ ] **External Services**: Can you reach external services from your machine?
- [ ] **Ports**: Are required ports available? `netstat -ano | findstr :8080`
- [ ] **Environment Variables**: Are all required env vars set?
- [ ] **Configuration**: Does `application.yml` have all required properties?
- [ ] **Spring Profiles**: Is the correct profile active?

---

## Getting Help

1. **Check logs first**: Most issues are visible in application logs
2. **Search existing docs**: Check `MULE_CONVERSION_GUIDE.md`, `CONFIG_MAPPING.md`
3. **Review generated report**: Check `sys-billing/report.md` for conversion notes
4. **Enable debug logging**: Set `logging.level.com.westfield.api: DEBUG`
5. **Check agent.md**: Review schema for expected data structures
6. **Run diagnostic tools**:
   ```bash
   mvn dependency:tree
   curl http://localhost:8080/actuator/configprops
   curl http://localhost:8080/actuator/env
   ```

