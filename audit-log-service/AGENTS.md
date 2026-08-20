# AGENTS.md - Audit Log Service Developer Guide

## Overview

**Audit Log Service** is a Spring Boot 3.3.1 tamper-evident audit logging system using **SHA-256 hash chain verification** to detect tampering. Core concept: each event stores a hash of its content plus the previous event's hash, creating an immutable chain.

**Key Principle**: Audit events are **append-only** - no updates/deletes after creation. Archives are permanent (immutable).

**Assignment Scope** (3 Scenarios):
- **Scenario A** (Core): Write API, Query API, Hash Chain, Verification Endpoint
- **Scenario B** (Extend): Retention Policy, Structured Redaction, Bulk Export
- **Scenario C** (Ambiguous): Compliance Reporting (requirement clarification required)

---

## Architecture: The Hash Chain

```
Event 1 (Genesis)           Event 2              Event 3
├─ content = "USER_LOGIN..."├─ content = "..."  ├─ content = "..."
├─ contentHash = sha256(content) ├─ previousHash = Event1.contentHash
├─ previousHash = "GENESIS_HASH"  └─ chainPosition = 2
└─ chainPosition = 1           └─ chainPosition = 3
```

**Hash computation order** (see `AuditEventService.buildEventContent`):
```
eventType|actorId|resourceType|resourceId|payload|timestamp
```
Order matters for consistent verification. Timestamp must be resolved once before hashing (use `request.getTimestamp()` if provided, else `LocalDateTime.now()`).

**Chain verification** (`ChainVerificationService`):
- Walk all events by `chainPosition` (1, 2, 3...)
- Verify `currentEvent.previousHash == previousEvent.contentHash`
- Genesis event: `previousHash == "GENESIS_HASH"`
- Both hashes must be 64 hex chars (SHA-256 output)

---

## Build & Run

```powershell
# Set Java 21
$env:JAVA_HOME='C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot'
$env:PATH = "$env:JAVA_HOME\bin;" + $env:PATH

# Run locally (H2 in-memory DB, port 8282)
mvn spring-boot:run

# Tests (H2 test profile)
mvn test

# Coverage report
target/site/jacoco/index.html
```

**Default Profile**: H2 embedded in-memory (`jdbc:h2:mem:audit_log_db`). DB schema auto-migrated via **Flyway** migrations in `src/main/resources/db/migration/`.

---

## Authentication & Authorization

**JWT-based stateless auth** with role-based access control.

### Roles (3 types)
- `AUDIT_WRITER`: Create events (`POST /api/v1/audit/events`)
- `AUDITOR`: Read/query events, verify chain
- `ADMIN`: All operations

### Key Classes
- `JwtService`: Generate/validate tokens, extract claims (JJWT 0.12.x API)
- `JwtAuthenticationFilter`: Intercept `Authorization: Bearer <token>` header
- `SecurityConfig`: Configure stateless session, CORS, endpoint access

### Config Properties (`application.properties`)
```ini
app.jwt.secret=${JWT_SECRET:defaultSecretKeyChangeInProduction...}
app.jwt.expiry-hours=24
```

**Important**: JWT secret must be 32+ chars (HMAC-SHA256 requirement). In production, set via `JWT_SECRET` env var.

---

## Key Files & Patterns

### Entities
- **AuditEvent** (`entity/AuditEvent.java`): 6 indexes, CLOB storage for JSON payload/redaction metadata
  - `contentHash`: Unique, SHA-256 (64 hex chars)
  - `chainPosition`: Unique, auto-incremented
  - `previousHash`: References previous event's content
  - `archived`: Immutable boolean (set via `markAsArchived()`)
  - Auditing fields: `createdBy`, `updatedBy` (auto-filled by Spring Data AuditingEntityListener)

- **User** (`entity/User.java`): Stores username, role, BCrypt password (strength 12)

- **RetentionPolicy** (`entity/RetentionPolicy.java`): Archive rules (e.g., after 365 days)

- **AuditEventRedaction** (`entity/AuditEventRedaction.java`): Track redacted fields + reason

### Services (Business Logic)
1. **AuditEventService**
   - `createAuditEvent()`: Hash computation, chain linking, dedup check
   - Query methods: by actor, resource, type, timestamp range, archived status
   - `verifyEventIntegrity()`, `archiveAuditEvent()`

2. **ChainVerificationService**
   - `verifyCompleteChain()`: Walk all events, validate chain integrity
   - `verifyEvent()`: Validate single event's hash + chain position

3. **RedactionService**: Redact sensitive fields while preserving original hash

4. **RetentionPolicyService**: Auto-archive old events per policy

5. **ComplianceReportingService**: Generate audit reports

6. **ExportService**: Export events (CSV, JSON)

### DTO Pattern
- **Request DTOs** (`dto/request/*`): Validated with `@Valid`, `@NotBlank`, `@Size` annotations
- **Response DTOs** (`dto/response/*`): Two variants:
  - `AuditEventResponse`: Minimal (for lists)
  - `AuditEventDetailResponse`: Full (includes `redactionMetadata`, `isGenesis`, `isRedacted`)

### Repository Methods (Spring Data JPA)
All paginated with `Pageable`. Key custom methods:
```java
findLastEvent()                          // Last event by chainPosition
findByChainPosition(long position)       // For verification
existsByContentHash(String hash)         // Dedup check
findByResourceTypeAndResourceId()        // Query by resource
findByActorId()                          // Query by user
findByTimestampRange()                   // Range query
findByArchivedTrue/False()               // Archived status
```

### Utilities
- **HashUtils**: `computeSha256(String content)` → 64-char hex string
- **Constants**: `GENESIS_HASH`, role names, event types, patterns
  - Event type pattern: `^[A-Z_]{1,100}$` (uppercase + underscores only)
  - Pagination: default 20, max 100 items

---

## API Conventions

### Endpoint Pattern
```
/api/v1/{resource}/{operation}
```

Examples:
- `POST /api/v1/audit/events` - Create event (201 Created)
- `GET /api/v1/audit/events` - List events (pagination: ?page=0&size=20)
- `GET /api/v1/audit/events/{id}` - Get event detail
- `GET /api/v1/audit/events/{id}/verify` - Verify event integrity
- `PUT /api/v1/audit/events/{id}/archive` - Archive event (immutable)

### Status Codes
- `201`: Created (event creation)
- `400`: Invalid request (validation failed, duplicate content)
- `401`: Unauthorized (missing/invalid JWT)
- `403`: Forbidden (insufficient role)
- `409`: Conflict (duplicate event hash)
- `500`: Server error

### Pagination Query Params
```
?page=0&size=20&sort=timestamp,desc
```

### Request Header
```
Authorization: Bearer eyJhbGc...
```

---

## Database & Migration

**Flyway** handles schema versioning (classpath:`db/migration/`).

### Migration Files
- `V1__initial_schema.sql`: Core tables (audit_events, users, retention_policies)
- `V2__add_audit_auditing_columns.sql`: Auditing fields (created_by, updated_by)

### Schema Design
- **audit_events**: 6 indexes on `chain_position`, `actor_id`, `(resource_type, resource_id)`, `event_type`, `timestamp`, `archived`
- **Unique Constraints**: `content_hash`, `chain_position`
- **CLOB Columns**: `payload` (JSON content), `redaction_metadata` (JSON)

**Why**: Hash chain = sequential access pattern. Resource queries are common (account audit trails). Timestamp range queries for compliance.

---

## Testing Patterns

### Integration Tests (Testcontainers + PostgreSQL)
Located in `src/test/java/com/schwab/audit/`. Test fixtures follow:
- `@SpringBootTest`: Full context
- `@Transactional`: Auto-rollback per test
- `@Testcontainers`: PostgreSQL container for realistic scenarios
- Mock JWT token generation for auth tests

### Common Test Patterns
```java
// Create event
AuditEventResponse response = service.createAuditEvent(request);
assertNotNull(response.getContentHash());
assertEquals(1, response.getChainPosition());

// Verify chain
assertTrue(chainService.verifyCompleteChain());

// Query
Page<AuditEventResponse> events = service.getEventsByResource(...);
assertEquals(expectedCount, events.getTotalElements());
```

---

## Common Workflows

### Adding a New Endpoint
1. Create endpoint in controller (e.g., `AuditEventController`)
2. Add `@PreAuthorize` role check
3. Call service method
4. Add OpenAPI `@Operation` + `@ApiResponses` annotations
5. Add integration test in `*ControllerIntegrationTest`

### Adding a New Query Type
1. Create `findBy*` method in `AuditEventRepository`
2. Call from `AuditEventService`
3. Expose via controller endpoint
4. Add index to `@Table` if performance-critical

### Debugging Hash Chain Issues
```java
// Verify specific event
AuditEvent event = repo.findById(eventId).get();
String recomputed = hashUtils.computeSha256(buildEventContent(...));
if (!event.getContentHash().equals(recomputed)) {
    log.error("Hash mismatch: stored={}, computed={}", 
             event.getContentHash(), recomputed);
}

// Verify chain segment
AuditEvent prev = repo.findByChainPosition(event.getChainPosition() - 1).get();
if (!event.getPreviousHash().equals(prev.getContentHash())) {
    log.error("Chain broken at position {}", event.getChainPosition());
}
```

---

## Critical Implementation Details

### Hash Timestamp Handling
**CRITICAL**: Timestamp used in hash must match persisted timestamp. Calling `LocalDateTime.now()` twice can create unverifiable events.

**Solution** (in `AuditEventService.createAuditEvent()`):
```java
LocalDateTime eventTimestamp = request.getTimestamp() != null
        ? request.getTimestamp()
        : LocalDateTime.now();  // Resolved once
String eventContent = buildEventContent(request, eventTimestamp);
String contentHash = hashUtils.computeSha256(eventContent);
AuditEvent event = AuditEvent.builder()
        .timestamp(eventTimestamp)  // Same instance
        .contentHash(contentHash)
        .build();
```

### Duplicate Detection
Events with identical content (same eventType, actor, resource, payload, timestamp) produce same hash → rejected.
```java
if (auditEventRepository.existsByContentHash(contentHash)) {
    throw new IllegalArgumentException("Event with identical content already exists");
}
```

### Archive is Immutable
Once archived, event cannot be unarchived. Use `event.markAsArchived()` (sets `archived = true` + `archivedAt = LocalDateTime.now()`).

### Chain Position Sequentiality
Always assign `chainPosition = lastEvent.getChainPosition() + 1` or `1` for genesis. No gaps allowed. Tests verify this.

---

## Scenario B: Retention & Redaction Patterns

### Retention Policy
**Files**: `entity/RetentionPolicy.java`, `service/RetentionPolicyService.java`

Soft-archive events older than N days without breaking chain:
```java
// Mark as archived without deleting from chain
event.markAsArchived();  // Sets archived=true, archivedAt=LocalDateTime.now()
repo.save(event);
```

**Key Design Constraint**: `ChainVerificationService` must skip verification of archived events. The hash chain remains intact; verification reports:
```text
Chain Status: VALID (N events, M archived)
```

**Database Consideration**: Add index on `(archived, timestamp)` for efficient archival queries.

### Structured Redaction
**Files**: `entity/AuditEventRedaction.java`, `service/RedactionService.java`

**The Problem**: Original payload hash = `H(eventType|actorId|...|payload|timestamp)`. If you remove/modify payload field, hash breaks.

**Solution (Immutable Redaction Record)**:
1. Store original `AuditEvent` with original hash (unchanged)
2. Create separate `AuditEventRedaction` record:
   ```java
   @Entity
   class AuditEventRedaction {
       Long auditEventId;
       String fieldName;        // e.g., "payload.accountNumber"
       String originalValue;    // Encrypted/hashed if sensitive
       String redactionReason;  // e.g., "PII_REMOVAL", "COMPLIANCE_GDPR"
       LocalDateTime redactedAt;
       String redactedBy;
   }
   ```
3. Update `AuditEvent.redactionMetadata` (JSON) to track applied redactions:
   ```json
   {
     "redactions": [
       {"field": "payload.accountNumber", "reason": "PII_REMOVAL", "at": "2026-08-20T14:30:00"}
     ]
   }
   ```
4. In queries, filter redacted fields from response but preserve hash integrity

**Critical**: Original `contentHash` never changes. Redaction is audit-trail only.

### Bulk Export
**Files**: `service/ExportService.java`, `controller/ExportController.java`

Export bundle must include enough metadata for independent verification:
```json
{
  "export_metadata": {
    "exported_at": "2026-08-20T15:00:00Z",
    "resource_id": "ACCT-12345",
    "event_count": 42,
    "start_chain_position": 100,
    "end_chain_position": 141,
    "export_signature": "sha256(concatenate all event hashes)"
  },
  "events": [
    {
      "id": 100,
      "chainPosition": 100,
      "contentHash": "...",
      "previousHash": "...",
      "timestamp": "...",
      "payload": {...}
    }
  ],
  "verification_info": {
    "hash_chain_segment_valid": true,
    "first_event_previous_hash": "...",
    "last_event_content_hash": "..."
  }
}
```

**Endpoint**: `GET /api/v1/audit/export?resourceId=X&format=json&includeRedactionMetadata=true`

---

## Scenario C: Compliance Reporting (Ambiguous Requirement)

### Requirement Clarification Framework
Before implementing, ask:

1. **Scope of "Access Audit"**
   - Does it mean: "Who accessed account data?" OR "Who has permission to access?"
   - Does it include: API calls, UI views, export operations, or all?

2. **Data Granularity**
   - Per-account? Per-user? Per-account+user+timestamp combination?
   - Should it track "viewed 100 records" or "viewed record X on date Y"?

3. **Retention Window**
   - How long must compliance reports cover? (1 year? 3 years? 7 years?)
   - Are archived records included in reports?

4. **Report Format & Consumers**
   - Who consumes the report? (Compliance team, auditors, regulators, SOC2 assessor?)
   - Format: JSON API, PDF download, CSV export, or real-time dashboard query?

5. **Detection Scope**
   - Only successful access? Or also denied/failed attempts?
   - Only business users or also system services?

### Example Clarified Requirement
**"Provide a compliance report showing all events (successful and failed) where actorId != system_account for a given resourceId, within a 90-day window, with immutable hash chain proof of non-tampering, exportable as JSON for external audit."**

### Implementation Patterns
```java
@Service
public class ComplianceReportingService {
    
    // Narrowly scoped: specific resource + time window + actor types
    public ComplianceReport generateAccessAudit(
        String resourceId,
        LocalDateTime from,
        LocalDateTime to,
        List<String> actorRoles    // Filter: exclude system accounts
    ) {
        // Query: events for this resource in timeframe
        // Filter: non-system actors
        // Include: chain verification proof for time window
        // Return: tamper-proof bundle
    }
    
    // Include chain verification for the report period
    public VerificationProof verifyReportChain(ComplianceReport report) {
        // Walk chain segment from report.firstChainPosition to report.lastChainPosition
        // Return: integrity status + breach details if any
    }
}

@RestController
@RequestMapping("/api/v1/compliance")
public class ComplianceController {
    
    @PostMapping("/reports/access-audit")
    @PreAuthorize("hasRole('AUDITOR') or hasRole('ADMIN')")
    public ResponseEntity<ComplianceReport> generateAccessAudit(
        @RequestParam String resourceId,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
        @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) { ... }
}
```

---

## Swagger/OpenAPI

Access at: `http://localhost:8282/swagger-ui.html`

All endpoints documented with:
- `@Operation`: Summary + description
- `@ApiResponses`: Status codes + schemas
- `@SecurityRequirement(name = "Bearer Authentication")`

Test endpoints via Swagger UI by pasting JWT token in "Authorize" dialog.

---

## Environment Variables

| Variable | Purpose | Example |
|----------|---------|---------|
| `JWT_SECRET` | HMAC-SHA256 signing key | 32+ char random string |
| `JAVA_HOME` | JDK 21 path | `C:\Program Files\Microsoft\jdk-21.0.12.8-hotspot` |

Default profile uses in-memory H2; change via `spring.profiles.active` or `application-{profile}.properties`.

---

## Production Readiness Checklist

### Security
- [ ] JWT secret minimum 32 chars, loaded from secure env var (not default)
- [ ] BCrypt strength = 12 (configured in `SecurityConfig`)
- [ ] No SQL injection: All queries use parameterized JPA methods
- [ ] No XSS: Payload stored as-is (not HTML-escaped) — clients responsible for escaping on display
- [ ] Authentication enforced on all `/api/v1/**` endpoints except `/auth/login`, `/auth/register`
- [ ] Rate limiting: Implement at API gateway (not in this service)
- [ ] CORS: Whitelist specific origins in `SecurityConfig` (currently `localhost:3000/4200/8080`)
- [ ] H2 console disabled in production (`spring.h2.console.enabled=false`)
- [ ] Password encoder strength = 12 (slow, resistant to brute force)

### Performance & Scalability
- [ ] Hash chain verification is O(n) — acceptable for audit logs, problematic for >1M records in single chain. Consider periodic snapshot + verify-from-snapshot.
- [ ] Indexes present for common queries (6 indexes on `audit_events`)
- [ ] Pagination enforced (max 100 items/page) to prevent OOM on large result sets
- [ ] Flyway migrations are idempotent (safe to run multiple times)
- [ ] HikariCP pool: 10 max, 2 min, good for moderate load

### Data Integrity
- [ ] Verify chain via `GET /api/v1/audit/verify` before production use
- [ ] Set retention policy and retention job before data volume grows
- [ ] Archive strategy tested for chain continuity (no false positives)
- [ ] Backup strategy documented (chain snapshots, off-system storage)
- [ ] Database transaction isolation level = READ_COMMITTED (default, acceptable for append-only)

### Operations
- [ ] Error logs go to stdout (Docker-friendly)
- [ ] Structured logging in place (timestamps, log levels, traceability)
- [ ] Health check endpoint (Spring Boot Actuator) — consider adding for K8s readiness
- [ ] Database migration automation (Flyway runs on startup)
- [ ] No hardcoded credentials in code

---

## Validation & Testing Strategy

### Chain Integrity Tests
**MUST validate tampering detection**:
1. Create 3+ events via API
2. Verify chain via `GET /api/v1/audit/verify` → status = "VALID"
3. **Directly modify a record in database** (e.g., change event 2's payload)
4. Verify chain again → status = "BROKEN", first violation at position = 2
5. Confirm hashes match before/after for unmodified events

```java
// Example test
@Test
void testChainTamperingDetection() throws Exception {
    // 1. Create events
    createAuditEvent("USER_LOGIN", "alice", ...);
    createAuditEvent("RECORD_UPDATED", "bob", ...);
    
    // 2. Verify chain is valid
    mvc.perform(get("/api/v1/audit/verify"))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.status").value("VALID"));
    
    // 3. Tamper directly (bypass service layer)
    AuditEvent event = repo.findByChainPosition(1).get();
    event.setPayload("TAMPERED");  // Change without updating hash
    repo.save(event);
    
    // 4. Verify chain detects tampering
    mvc.perform(get("/api/v1/audit/verify"))
       .andExpect(status().isOk())
       .andExpect(jsonPath("$.status").value("BROKEN"))
       .andExpect(jsonPath("$.firstBrokenPosition").value(2));  // Event 2 can't verify against tampered 1
}
```

### Redaction Tests (Scenario B)
- Redacted fields must not appear in query responses
- Original hash must remain unchanged
- Redaction metadata must be queryable/auditable

### Archive Tests (Scenario B)
- Archived events must not break chain
- `verifyCompleteChain()` must handle archived status correctly
- Archive is immutable (cannot unarchive)

### Export Tests (Scenario B)
- Export bundle must be independently verifiable
- Export signature must match recomputed hash
- Export includes enough chain metadata

---

## AI-Assisted Workflow & Commit Strategy

### How to Use AI Effectively
1. **Define Task Intent**: Use AI to brainstorm design, not as copy-paste code generator
2. **Validate AI Output**: Run tests, linting, manual review before committing
3. **Document Decisions**: Explain why you accepted/rejected AI suggestions in commit messages

### Commit Message Pattern (with AI Traceability)
```
[SCENARIO-A] Implement chain verification endpoint

- Add ChainVerificationService with verifyCompleteChain() logic
- Expose GET /api/v1/audit/verify REST endpoint
- Include chain gap detection and tamper position reporting

AI-Assisted:
- Copilot: Generated initial service structure + hash comparison logic
- Action: Reviewed, refactored loop for clarity, added logging
- Validation: Ran integration tests, verified chain break detection works

Acceptance: ✓ All tests pass, chain tamper detection validated
```

### Documentation Pattern for Ambiguous Requirements (Scenario C)
**File**: `docs/REQUIREMENT_CLARIFICATION_C.md`

```markdown
## Compliance Reporting — Requirement Clarification

### Original Requirement
"Regulators need to be able to audit access to client account data."

### Ambiguities Identified
1. Does "audit access" mean: (a) who accessed data, (b) when they accessed it, (c) what they did?
2. Access via: API? UI? Export?
3. Report window: Rolling 90 days? Calendar year? Custom range?

### Clarified Requirement (Assumed)
"Provide a read-only compliance report endpoint that lists all non-system actors who accessed a given account within a time window, with immutable hash chain proof."

### Design Decision
- Implement `GET /api/v1/compliance/access-audit?resourceId=X&from=Y&to=Z`
- Return: Filtered events + chain verification proof
- Scope out: Real-time dashboard, alert system, auto-remediation

### Tradeoff
- Include: Audit trail of access
- Exclude: Granular field-level access tracking (too expensive)
```

---

## Key Gotchas & Reminders

1. **No Update After Create**: AuditEvent is append-only. Design queries accordingly.
2. **Hash Chain = Sequential**: Always verify `chainPosition` sequentiality. Tests use `verifyCompleteChain()`.
3. **Timestamp Precision**: Millisecond precision matters for hashing. Mismatch = unverifiable event.
4. **Role-Based Access**: Controllers check roles via `@PreAuthorize`. Unauthenticated = 401, insufficient role = 403.
5. **Pagination Limits**: Max 100 items per page (`PAGINATION_MAX_SIZE`). Clients must paginate for large result sets.
6. **Flyway Baseline**: First migration is V1. Never delete/rename existing migrations. Create new V3, V4, etc.
7. **H2 Console**: Enabled in dev (`/h2-console`). Disable in production via `spring.h2.console.enabled=false`.
8. **Archive is One-Way**: Once archived, cannot be unarchived. Design workflows accordingly.
9. **Redaction ≠ Deletion**: Redacted fields still tracked; original hash unchanged. Chain verification not affected.
10. **Chain Verification is O(n)**: Full chain walk is expensive for >1M records. Consider periodic snapshots.
11. **Genesis Event**: Every chain starts with `previousHash = "GENESIS_HASH"`. First event must be chainPosition=1.
12. **Duplicate Prevention**: Same content (eventType+actor+resource+payload+timestamp) produces same hash → rejected. Client must retry with different timestamp.

---

## Risk Analysis & Trade-Offs

### Risk: Hash Chain Scalability
**Problem**: Verifying complete chain requires O(n) database queries + hash computations.
**Impact**: For 10M records, verification takes minutes. Not suitable for real-time endpoints.
**Mitigation**:
- Add periodic snapshots: `SnapshotEntity { position, verifiedAt, snapshotHash }`
- Verify only from last snapshot onwards: O(delta), not O(n)
- Trade-off: Snapshot must be atomic + immutable

### Risk: Timestamp Collisions
**Problem**: Two events with identical timestamp, actor, resource → same hash → duplicate detected.
**Impact**: Rapid event streams (microsecond intervals) may collide if timestamp resolution is low.
**Mitigation**: Use nanosecond-precision timestamps or add sequence number to content.
**Trade-off**: Schema change, client coordination needed.

### Risk: Redaction Information Leakage
**Problem**: `AuditEventRedaction` table reveals what was redacted + when + by whom.
**Impact**: Compliance officers can infer which records had sensitive data.
**Mitigation**: Encrypt `AuditEventRedaction` at rest; restrict queries to compliance-cleared users.
**Trade-off**: Performance cost of decryption + access control complexity.

### Risk: Archive Reversal
**Problem**: Accidental archive of live data; no way to unarchive.
**Impact**: Data becomes inaccessible to operational queries.
**Mitigation**: Soft-delete only (mark archived=true, don't truncate). Require explicit approval before archive.
**Trade-off**: Requires retention policy review process + audit trail.

### Risk: Chain Verification False Positives
**Problem**: If archive logic is buggy, archived records may incorrectly report as "chain broken."
**Impact**: Regulators lose confidence in verification endpoint.
**Mitigation**: Extensive testing of archive + verification interaction. Document expected behavior.
**Trade-off**: Extended test cycle for Scenario B.

### Risk: JWT Expiration & Compliance Reports
**Problem**: Generated report's validity depends on JWT lifetime. Report shared after token expires?
**Impact**: Can't re-verify report signature.
**Mitigation**: Embed chain verification proof (hashes, signatures) directly in export bundle, not just token.
**Trade-off**: Larger payload, duplication.

---

## Quick Checklist for AI Agents

### Scenario A (Core)
- [ ] Run with `mvn spring-boot:run` before modifying core hash logic
- [ ] Create event → Query event → Verify chain (3-step happy path working)
- [ ] Verify `ChainVerificationService.verifyCompleteChain()` passes after changes
- [ ] Ensure timestamps in hash match persisted timestamps (use single `LocalDateTime.now()` call)
- [ ] Add `@PreAuthorize` to new endpoints
- [ ] Add OpenAPI annotations for Swagger docs
- [ ] Create integration test in `*ControllerIntegrationTest` or `*ServiceTest`
- [ ] Check pagination defaults (20 items, max 100)
- [ ] Validate DTOs with annotations (`@Valid`, `@NotBlank`, `@Size`)
- [ ] Use `AuditEventRepository.findLastEvent()` to link new events to chain
- [ ] Never modify `AuditEvent` after `@PrePersist` (append-only design)
- [ ] Test chain tampering detection (modify DB directly, verify detects break)

### Scenario B (Retention & Redaction)
- [ ] Retention: Archive doesn't break chain, `verifyCompleteChain()` still passes
- [ ] Redaction: Original hash unchanged, redaction metadata tracked separately
- [ ] Redaction: Redacted fields filtered from responses but hash verified
- [ ] Export: Bundle includes chain segment proof + verification metadata
- [ ] Export: Export signature recomputable by recipient
- [ ] Test: Create event → Redact field → Export → Verify independently
- [ ] Add index on `(archived, timestamp)` for archive queries
- [ ] Document redaction approach (immutable record pattern chosen)

### Scenario C (Compliance)
- [ ] Requirement clarified in writing (ambiguities resolved)
- [ ] Design documented: assumptions, scoped features, rationale
- [ ] Endpoint implemented & tested with role-based access
- [ ] Report includes chain verification proof
- [ ] Compliance report can be independently verified by recipient
- [ ] Scope boundaries documented (what IS/ISN'T included)

### General
- [ ] All tests pass: `mvn test`
- [ ] No compilation warnings
- [ ] Code formatted consistently
- [ ] Commits include AI traceability (prompts, accept/reject decisions)
- [ ] ATTESTATION.md filled out (name, email, dates, statement)
- [ ] README includes: how to run, how to test, what scenarios are implemented
- [ ] Architecture doc includes: data model, API design, key decisions, hash algorithm choice, trade-offs
- [ ] AI usage log documents: what was prompted, what accepted/modified/rejected, why
- [ ] Final engineering summary: plan, rationale, risks, assumptions, limitations

