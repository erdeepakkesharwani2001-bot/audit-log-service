# Audit Log Service

A Spring Boot 3.3.1 service for tamper-evident audit logging, retention, redaction, compliance reporting, and secure access.

## Features

- JWT authentication and authorization
- append-only audit events with hash chaining
- chain verification and tamper detection
- retention policy handling and archival
- redaction tracking and metadata
- export and compliance reporting endpoints
- H2 in-memory profile for local testing

## Run locally

Use Java 21.

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
mvn spring-boot:run
```

## Test

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH
mvn test
```

## API docs

Open Swagger UI at:

http://localhost:8282/swagger-ui.html

## Notes

The default application profile uses H2; the test profile is configured for H2 in test mode.
