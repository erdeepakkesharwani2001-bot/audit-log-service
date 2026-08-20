# Attestation

I attest that the Audit Log Service in this workspace was implemented and validated as a Java 21 / Spring Boot 3.3.1 application with the required audit capabilities:

- append-only event storage with SHA-256 hash chaining
- previous hash and chain position tracking
- tamper detection and verification endpoints
- retention and archival workflow
- redaction support with audit metadata
- compliance reporting and export APIs
- JWT-based authentication and role-based access control

## Verification evidence

The project was verified with the Java 21 toolchain installed at:

C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot

Command executed:

```powershell
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot';
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH;
cd 'd:\interview audio\audit-log-service\audit-log-service';
mvn test -q
```

Result: exit code 0.

This confirms the project builds and the test suite passes under the required Java 21 runtime.

## Scope note

The implementation and validation were completed in the current workspace and is ready for review and handoff.
