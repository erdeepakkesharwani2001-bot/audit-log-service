# Testing Strategy & Approach

## Test Coverage Overview

```
┌─────────────────────────────────────────────────────────┐
│         Audit Log Service - Test Pyramid               │
├─────────────────────────────────────────────────────────┤
│                                                         │
│                   E2E & Live Defense Tests             │
│               (Manual API validation)                  │
│                                                         │
│          ┌────────────────────────────────┐           │
│          │  Integration Tests              │           │
│          │  - Controller → DB flow         │           │
│          │  - Auth + Authorization         │           │
│          │  - Data persistence             │           │
│          │  Files: *IntegrationTest.java   │           │
│          └────────────────────────────────┘           │
│                                                         │
│     ┌───────────────────────────────────────────┐     │
│     │         Unit Tests                        │     │
│     │  - Service business logic                │     │
│     │  - Hash computation                      │     │
│     │  - Chain verification                    │     │
│     │  - Query filtering                       │     │
│     │  Files: *ServiceTest.java, *UtilTest.java│     │
│     └───────────────────────────────────────────┘    │
│                                                         │
└─────────────────────────────────────────────────────────┘
```

---

## Scenario A: Core Audit Log Testing

### Unit Tests (Service Layer)

#### AuditEventService Tests
```java
✓ testCreateAuditEvent_Success
  - Creates event with valid request
  - Asserts: id != null, chainPosition = 1, contentHash matches

✓ testCreateAuditEvent_ChainLinking
  - Creates 2+ events
  - Asserts: event2.previousHash == event1.contentHash
  - Asserts: event2.chainPosition = 2

✓ testCreateAuditEvent_DuplicateDetection
  - Attempts to create 2 events with identical content
  - Asserts: IllegalArgumentException thrown
  - Asserts: "identical content" error message

✓ testCreateAuditEvent_HashComputation
  - Creates event with known content
  - Asserts: contentHash matches SHA-256(content)
  - Asserts: 64 hex character output

✓ testGetAuditEventById_Found
  - Creates event, retrieves by ID
  - Asserts: retrieved event matches created

✓ testGetAuditEventById_NotFound
  - Retrieves non-existent event ID
  - Asserts: NoSuchElementException thrown

✓ testGetEventsByActor_FilterCorrect
  - Creates events for actor1, actor2, actor1
  - Asserts: filter by actor1 returns 2 events
  - Asserts: correct event details

✓ testGetEventsByResource_FilterCorrect
  - Creates events for resource A, B, A
  - Asserts: filter by A returns 2 events

✓ testGetEventsByType_FilterCorrect
  - Creates events of type X, Y, X
  - Asserts: filter by type X returns 2 events

✓ testGetEventsByTimestampRange_InclusiveBoundaries
  - Creates events at T1, T2, T3
  - Asserts: range [T1, T2] returns 2 events
  - Asserts: boundaries included

✓ testGetUnarchivedEvents_FilterCorrect
  - Creates 3 events, archives 1
  - Asserts: unarchived filter returns 2 events

✓ testGetArchivedEvents_FilterCorrect
  - Creates 3 events, archives 1
  - Asserts: archived filter returns 1 event

✓ testGetTotalEventCount_Correct
  - Creates 5 events
  - Asserts: count returns 5

✓ testArchiveAuditEvent_Success
  - Creates event, archives it
  - Asserts: archived = true
  - Asserts: archivedAt != null
  - Asserts: cannot archive again (IllegalArgumentException)

✓ testVerifyEventIntegrity_Valid
  - Creates event
  - Asserts: verifyEventIntegrity returns true

✓ testVerifyEventIntegrity_Invalid
  - Creates event, corrupts hash in DB
  - Asserts: verifyEventIntegrity returns false
```

#### ChainVerificationService Tests
```java
✓ testVerifyCompleteChain_Genesis
  - Chain with 1 event
  - Asserts: previousHash == "GENESIS_HASH"
  - Asserts: verification passes

✓ testVerifyCompleteChain_ValidChain
  - Creates 3 events
  - Asserts: verification passes
  - Asserts: all hashes link correctly

✓ testVerifyCompleteChain_BrokenChain_FirstEvent
  - Creates 3 events, corrupts event 1 hash
  - Asserts: verification fails
  - Asserts: detects breach at position 2

✓ testVerifyCompleteChain_BrokenChain_MiddleEvent
  - Creates 3 events, corrupts event 2 hash
  - Asserts: verification fails
  - Asserts: detects breach at position 2 or 3

✓ testVerifyCompleteChain_BrokenChain_PreviousHashMismatch
  - Creates 3 events, changes event 2 previousHash
  - Asserts: verification fails

✓ testVerifyCompleteChain_ChainGap
  - Creates events at position 1, 3 (missing 2)
  - Asserts: verification fails
  - Asserts: gap detected at position 2

✓ testVerifyCompleteChain_InvalidHashFormat
  - Creates event with short hash (not 64 chars)
  - Asserts: verification fails
  - Asserts: "Invalid content hash format" error

✓ testVerifyCompleteChain_EmptyChain
  - No events created
  - Asserts: verification passes (empty is valid)

✓ testVerifyEvent_HashMismatch
  - Creates event
  - Asserts: manual recomputation matches stored hash
  - Asserts: verifyEvent returns true
  - Changes payload, verifies
  - Asserts: verification fails

✓ testVerifyEvent_ChainPositionSequential
  - Creates 3 events
  - Asserts: chainPosition = 1, 2, 3 (sequential)
  - Asserts: verification passes
```

#### HashUtils Tests
```java
✓ testComputeSha256_Known
  - Input: "test"
  - Asserts: output is deterministic (same every time)
  - Asserts: 64 hex characters

✓ testComputeSha256_Empty
  - Input: ""
  - Asserts: produces valid SHA-256 hash

✓ testComputeSha256_LongContent
  - Input: 1MB string
  - Asserts: computes without error
  - Asserts: 64 hex characters

✓ testVerifyHash_Match
  - Asserts: verifyHash(stored, computed) returns true

✓ testVerifyHash_Mismatch
  - Asserts: verifyHash(different, hashes) returns false

✓ testVerifyHash_NullHandling
  - Asserts: verifyHash(null, hash) returns false
  - Asserts: verifyHash(hash, null) returns false
```

### Integration Tests

#### AuditEventController Tests
```java
✓ testCreateAuditEvent_Endpoint_201
  POST /api/v1/audit/events with valid JSON
  Asserts: HTTP 201 Created
  Asserts: Response contains id, contentHash, chainPosition

✓ testCreateAuditEvent_Endpoint_400_InvalidRequest
  POST with missing required field (eventType)
  Asserts: HTTP 400 Bad Request
  Asserts: Error message present

✓ testCreateAuditEvent_Endpoint_401_Unauthorized
  POST without JWT token
  Asserts: HTTP 401 Unauthorized

✓ testCreateAuditEvent_Endpoint_403_InsufficientRole
  POST with AUDITOR role (needs AUDIT_WRITER or ADMIN)
  Asserts: HTTP 403 Forbidden

✓ testCreateAuditEvent_Endpoint_409_DuplicateContent
  POST same content twice
  First request: HTTP 201
  Second request: HTTP 409 Conflict

✓ testListAuditEvents_Endpoint_200
  GET /api/v1/audit/events
  Asserts: HTTP 200
  Asserts: Page object returned with totalElements, totalPages

✓ testListAuditEvents_Endpoint_Pagination
  GET /api/v1/audit/events?page=0&size=10
  Asserts: Page size = 10
  Asserts: Page number = 0

✓ testListAuditEvents_Endpoint_MaxPageSizeEnforced
  GET /api/v1/audit/events?size=200 (max is 100)
  Asserts: Page size capped at 100

✓ testGetAuditEventById_Endpoint_200
  GET /api/v1/audit/events/1
  Asserts: HTTP 200
  Asserts: Full event details returned

✓ testGetAuditEventById_Endpoint_404
  GET /api/v1/audit/events/9999 (non-existent)
  Asserts: HTTP 404 Not Found

✓ testSearchByActor_Endpoint_200
  GET /api/v1/audit/events/search/by-actor?actorId=alice
  Asserts: HTTP 200
  Asserts: Only events with actorId=alice returned

✓ testSearchByResource_Endpoint_200
  GET /api/v1/audit/events/search/by-resource?resourceType=ACCOUNT&resourceId=123
  Asserts: HTTP 200
  Asserts: Filtering by both resource type and ID

✓ testSearchByType_Endpoint_200
  GET /api/v1/audit/events/search/by-type?eventType=USER_LOGIN
  Asserts: HTTP 200
  Asserts: Only matching event type returned

✓ testSearchByTimeRange_Endpoint_200
  GET /api/v1/audit/events/search/by-time-range?startTime=2026-08-20T10:00:00&endTime=2026-08-20T11:00:00
  Asserts: HTTP 200
  Asserts: Only events within range returned

✓ testVerifyChain_Endpoint_200_Valid
  POST /api/v1/audit/events/verify-chain (with valid chain)
  Asserts: HTTP 200
  Asserts: valid: true in response

✓ testVerifyChain_Endpoint_200_Broken
  POST /api/v1/audit/events/verify-chain (after tampering)
  Asserts: HTTP 200
  Asserts: valid: false in response
  Asserts: message indicates chain failure

✓ testVerifyEvent_Endpoint_200
  POST /api/v1/audit/events/1/verify
  Asserts: HTTP 200
  Asserts: valid: true or false

✓ testArchiveEvent_Endpoint_200
  POST /api/v1/audit/events/1/archive (ADMIN only)
  Asserts: HTTP 200
  Asserts: archived: true in response

✓ testArchiveEvent_Endpoint_403_NonAdmin
  POST /api/v1/audit/events/1/archive (non-ADMIN)
  Asserts: HTTP 403 Forbidden

✓ testGetStatistics_Endpoint_200
  GET /api/v1/audit/events/stats/summary
  Asserts: HTTP 200
  Asserts: totalEvents field present
```

---

## Scenario B: Retention & Redaction Testing

### Unit Tests

#### RetentionPolicyService Tests
```java
✓ testArchiveEventsOlderThan_Success
  - Creates events at T-400 days, T-200 days, T-10 days
  - Archives events older than 365 days
  - Asserts: 1 event archived (T-400)
  - Asserts: 2 events not archived

✓ testArchiveEventsOlderThan_ChainIntegrity
  - Creates 3 events, archives oldest
  - Verifies chain
  - Asserts: verification still passes
  - Asserts: archived event doesn't break chain

✓ testArchiveEventsOlderThan_UpdateTimestamp
  - Archives event
  - Asserts: archivedAt != null
  - Asserts: timestamp precision correct
```

#### RedactionService Tests
```java
✓ testRedactEvent_FieldRemoved
  - Creates event with payload { "accountNumber": "1234", "name": "Alice" }
  - Redacts "accountNumber"
  - Asserts: payload now has "accountNumber": "***REDACTED***"
  - Asserts: "name" still present

✓ testRedactEvent_HashUnchanged
  - Creates event, stores contentHash
  - Redacts field
  - Retrieves event, recomputes hash using ORIGINAL content
  - Asserts: stored hash == recomputed hash
  - Critical: Original hash untouched!

✓ testRedactEvent_MetadataTracked
  - Redacts event
  - Asserts: redactionMetadata JSON contains:
    - fields: ["accountNumber"]
    - reason: "PII_REMOVAL"
    - redactedBy: "admin"
    - redactedAt: timestamp

✓ testRedactEvent_CannotRedactTwice
  - Redacts event once
  - Attempts to redact again
  - Asserts: IllegalArgumentException thrown

✓ testGetRedactionHistory_Found
  - Redacts event
  - Asserts: getRedactionHistory returns record

✓ testIsEventRedacted_True
  - Redacted event
  - Asserts: isEventRedacted returns true

✓ testIsEventRedacted_False
  - Non-redacted event
  - Asserts: isEventRedacted returns false
```

#### ExportService Tests
```java
✓ testExportAsJson_Valid
  - Creates 3 events
  - Exports as JSON
  - Asserts: Valid JSON structure
  - Asserts: All 3 events present
  - Asserts: contentHash, previousHash fields present

✓ testExportAsCSV_Valid
  - Creates 3 events
  - Exports as CSV
  - Asserts: Header row correct (ID,Event Type,...)
  - Asserts: 3 data rows (plus header = 4 lines)
  - Asserts: Fields comma-separated

✓ testExportAsCSV_EscapeQuotes
  - Creates event with comma or quote in actorId
  - Exports as CSV
  - Asserts: Special chars escaped properly
```

### Integration Tests

#### ComplianceController Tests
```java
✓ testArchiveRetention_Endpoint_200
  POST /api/v1/compliance/retention/apply?retentionDays=365
  Asserts: HTTP 200
  Asserts: archivedCount in response

✓ testRedactEvent_Endpoint_200
  POST /api/v1/compliance/redact/1
  Body: { "fields": ["accountNumber"], "reason": "PII_REMOVAL", "redactedBy": "admin" }
  Asserts: HTTP 200
  Asserts: "Event redacted successfully" message

✓ testRedactEvent_Endpoint_400_InvalidRequest
  POST with missing "fields" parameter
  Asserts: HTTP 400 Bad Request

✓ testExportJson_Endpoint_200
  GET /api/v1/compliance/export/json
  Asserts: HTTP 200
  Asserts: Content-Disposition header present (filename=audit-events.json)
  Asserts: Valid JSON body

✓ testExportCsv_Endpoint_200
  GET /api/v1/compliance/export/csv
  Asserts: HTTP 200
  Asserts: Content-Type: text/csv header
  Asserts: Content-Disposition: attachment header
  Asserts: Valid CSV body
```

---

## Scenario C: Compliance Reporting Testing

### Unit Tests

#### ComplianceReportingService Tests
```java
✓ testGenerateComplianceReport_DateRange
  - Creates events at various timestamps
  - Generates report for specific date range
  - Asserts: Only events within range included
  - Asserts: Report structure complete

✓ testGenerateUserAuditTrail_FilterByActor
  - Creates events for alice, bob, alice
  - Generates trail for alice
  - Asserts: 2 events returned
  - Asserts: All have actorId=alice

✓ testGenerateResourceAuditTrail_FilterByResource
  - Creates events for resource A, B, A
  - Generates trail for A
  - Asserts: 2 events returned
  - Asserts: All have correct resource

✓ testPerformComplianceCheck_Status
  - Generates compliance check
  - Asserts: Response includes status, summary
  - Asserts: No validation errors (or lists them)
```

### Integration Tests

#### Compliance Endpoints Tests
```java
✓ testComplianceReport_Endpoint_200
  GET /api/v1/compliance/reports/compliance?startDate=X&endDate=Y
  Asserts: HTTP 200
  Asserts: Report data returned

✓ testUserAuditTrail_Endpoint_200
  GET /api/v1/compliance/reports/user-audit-trail?actorId=alice&days=30
  Asserts: HTTP 200
  Asserts: Events for alice returned

✓ testResourceAuditTrail_Endpoint_200
  GET /api/v1/compliance/reports/resource-audit-trail?resourceType=ACCOUNT&resourceId=123
  Asserts: HTTP 200
  Asserts: Events for resource returned

✓ testComplianceCheck_Endpoint_200
  POST /api/v1/compliance/check
  Asserts: HTTP 200
  Asserts: Compliance status returned

✓ testAuditReport_Endpoint_200
  GET /api/v1/compliance/reports/audit
  Asserts: HTTP 200
  Asserts: Audit report data returned
```

---

## Critical Chain Tampering Validation

### How We Prove Tamper Detection Works

**Test Case: Detect Hash Modification**

```sql
-- Setup: Create 3 events
POST /api/v1/audit/events { eventType: "EVENT1", ... }  → id=1, position=1
POST /api/v1/audit/events { eventType: "EVENT2", ... }  → id=2, position=2
POST /api/v1/audit/events { eventType: "EVENT3", ... }  → id=3, position=3

-- Verify chain is valid initially
POST /api/v1/audit/events/verify-chain
Response: { "valid": true, "totalEvents": 3 }

-- Tamper: Directly modify event 2 in database
UPDATE audit_events 
SET payload = '{"tampered": true}' 
WHERE id = 2;

-- NOTE: contentHash NOT updated, so it now misrepresents payload

-- Verify chain again (will detect tampering)
POST /api/v1/audit/events/verify-chain
Response: { 
  "valid": false, 
  "message": "Chain integrity check failed",
  "firstBrokenPosition": 3 
}
-- Event 3's previousHash (points to event 2) no longer matches
-- Event 2's contentHash (which is now wrong)
```

**Why this works**:
1. Event 2's stored contentHash was: `SHA256("original payload")`
2. We changed payload but left contentHash unchanged
3. Event 3's previousHash still points to Event 2's contentHash
4. When verification recomputes Event 2's hash from modified payload, it gets different value
5. Mismatch detected → tampering confirmed ✓

---

## Test Data Strategy

### Fixtures
```java
// User data
- User alice (AUDIT_WRITER)
- User bob (AUDITOR)
- User admin (ADMIN)

// Event data
@BeforeEach
void setupTestData() {
    // Create 3 events for chain testing
    event1 = createAuditEvent("USER_LOGIN", "alice", "USER", "alice123");
    event2 = createAuditEvent("RECORD_UPDATED", "bob", "ACCOUNT", "ACC-001");
    event3 = createAuditEvent("PERMISSION_GRANTED", "admin", "ROLE", "AUDITOR");
}

@AfterEach
void cleanup() {
    // @Transactional auto-rollback in tests
}
```

### Test Database
- **Dev**: H2 in-memory (`jdbc:h2:mem:audit_log_db`)
- **CI/CD**: H2 or Testcontainers PostgreSQL
- **Schema**: Flyway V1 + V2 migrations auto-run

---

## Coverage Report

### Target Coverage
- Unit tests: 80%+ code coverage
- Integration tests: 100% endpoint coverage
- Critical paths: Chain verification, hash computation (100%)

### Generated Report
```bash
mvn test jacoco:report
# Open: target/site/jacoco/index.html
```

---

## Limitations & Trade-Offs

### NOT Tested (and why)

| What | Why Not | Alternative |
|------|---------|-------------|
| Concurrent writes | Single-threaded tests sufficient | Load test if needed |
| Distributed systems | Out of scope (single service) | K8s testing when deployed |
| Network failures | Mock or integration test | Chaos testing if needed |
| Hardware failure | Database responsibility | Backup strategy docs |
| Quantum attacks | Not yet relevant (2026) | Post-quantum crypto later |

### Performance Testing NOT Included
- Reason: Requires production-like data volume (>1M events)
- Recommendation: Run load test with 1M events in staging before production

### Security Testing NOT Included
- Reason: Covered by architectural design + review
- Recommendation: Penetration test by security team before deployment

---

## Test Execution

```bash
# Run all tests
mvn test

# Run specific test class
mvn test -Dtest=ChainVerificationServiceTest

# Run with coverage
mvn test jacoco:report

# View coverage report
open target/site/jacoco/index.html
```

---

## Continuous Integration Checklist

Before merging to main:
- [ ] All unit tests pass
- [ ] All integration tests pass
- [ ] No compilation warnings
- [ ] Code coverage >= 80%
- [ ] Chain tampering test passes
- [ ] Manual API smoke test


