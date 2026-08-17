# Automated Agent Instructions and Schemas

## Purpose

This document defines the schemas, field requirements, and detailed specifications that the automated conversion agent
must follow when converting sapi-billing (Mule) to sys-billing (Spring Boot).

## Required Output Files

After processing, the agent must produce:


1. **report.md** - Human-readable summary (see Report Markdown section below)
2. **patches/** or git commits - All code changes
3. **build.log** - Maven build output (if build was executed)

## Report Markdown (report.md) Structure

```markdown
# Conversion Report for [FLOW_NAME or FULL_CONVERSION]

## Summary

- Source: sapi-billing
- Target: sys-billing
- Conversion Date: [ISO-8601]
- Status: [NOT_STARTED | IN_PROGRESS | COMPLETED | FAILED]
- Completion: X%

## Changes Made

### Config Changes

- List of properties/yml configurations changed

### Code Changes

- Flows converted to RestControllers
- Services/Transforms converted to mappers
- Tests added

### Kustomize Changes

- Environment-specific patches applied

## Issues and Blockers

### Errors (must fix)

- List of blocking issues

### Warnings (should fix)

- List of potential issues

### Notes (FYI)

- Informational items

## Files Affected

- List of modified/created files with line counts

## Next Steps

- Manual actions required
- Remaining work

## Build Results

- Build Status: SUCCESS | FAILURE | SKIPPED
- Test Results: X passed, Y failed, Z skipped
- Coverage: X%
```

## Conversion Mappings

### Mule to Spring Boot Flow Mapping

- **Mule HTTP Listener** → Spring `@RestController` + `@RequestMapping`
- **Mule Flow** → Spring Service method or Controller endpoint
- **Mule Set Payload** → Java assignment or `ResponseEntity<T>`
- **Mule DataWeave Transform** → Java mapper class (e.g., `EntityMapper`)
- **Mule Choice Router** → Java if-else or Strategy pattern
- **Mule For Each** → Java foreach or Stream API
- **Mule Try/Catch** → Java try-catch block
- **Mule Global Error Handler** → Spring `@ControllerAdvice` or `@ExceptionHandler`
- **Mule Connector (HTTP/WMQ/DB)** → Spring RestTemplate / JdbcTemplate / JMS

### Properties to YAML Mapping

- Mule `application-<env>.properties` → `src/main/resources/application.yml`
- Environment variables → Kustomize patches in `kustomize/environments/<env>/`
- Secrets → `kustomize/base/secrets.yaml` (with placeholders)

## Error Handling Requirements

If the agent encounters:

1. **Unmappable Flow** - Create manual instruction in `sys-billing/manual/[name].todo.md`
2. **Dependency Missing** - Add to `needs_input[]` in JSON response
3. **Build Failure** - Log output to `sys-billing/build.log`, include in report.md

## Validation Checklist

Before marking conversion complete, verify:

- [ ] All HTTP flows have corresponding Spring RestControllers
- [ ] All properties mapped to application.yml or kustomize
- [ ] All tests pass (`mvn clean test` succeeds)
- [ ] Jar builds successfully (`mvn clean package` succeeds)
- [ ] No hardcoded secrets in source code
- [ ] Dockerfile and docker-compose.yml updated
- [ ] Kustomize patches for all environments valid
- [ ] report.md documents all changes

## Notes for Agent Implementation

- Always commit atomic changes with clear messages
- Preserve original Mule XML files in `archive/` for reference
- Use standard Spring Boot conventions (camelCase → snake_case for properties)
- Maintain Java 21 compatibility
- Follow the pom.xml dependencies already defined
- Respect Spring Security and OAuth2 configurations
- Use Spring AOP where Mule aspects exist


