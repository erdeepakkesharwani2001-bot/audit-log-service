# Architecture Overview - Audit Log Service

## System Overview

The Audit Log Service is a tamper-evident logging system built on Spring Boot 3.3.1 that maintains an immutable, verifiable history of events using SHA-256 hash chain cryptography.

**Core Principle**: Every event is append-only and linked to the previous event via cryptographic hash, making any tampering immediately detectable.

---

## Architecture Components

```
┌─────────────────────────────────────────────────────────────┐
│                     Client Applications                      │
│                 (JWT-authenticated REST calls)               │
└────────────┬────────────────────────────────────┬────────────┘
             │                                    │
    ┌────────▼─────────┐              ┌──────────▼────────┐
    │   Auth Controller │              │ Audit Controller  │
    │  (Login/Register) │              │  (Write/Query)    │
    └────────┬─────────┘              └──────────┬────────┘
             │                                    │
    ┌────────▼─────────────────────────────────┐ │
    │        Spring Security Filter Chain      │ │
    │  - JWT validation                        │ │
    │  - Role-based access (@PreAuthorize)     │ │
    │  - CORS                                  │ │
    └────────┬─────────────────────────────────┘ │
             │                                    │
    ┌────────▼─────────────────────────────────┐ │
    │          Service Layer                   │ │
    │  ┌─────────────────────────────────────┐ │ │
    │  │ AuditEventService                   │─┼─┘
    │  │ - Hash computation                  │ │
    │  │ - Chain linking                     │ │
    │  │ - Query operations                  │ │
    │  └─────────────────────────────────────┘ │
    │                                           │
    │  ┌─────────────────────────────────────┐ │
    │  │ ChainVerificationService            │ │
    │  │ - Complete chain validation         │ │
    │  │ - Individual event verification     │ │
    │  │ - Tamper detection                  │ │
    │  └─────────────────────────────────────┘ │
    │                                           │
    │  ┌─────────────────────────────────────┐ │
    │  │ RedactionService (Scenario B)       │ │
    │  │ - Field-level redaction             │ │
    │  │ - Preserve original hash            │ │
    │  │ - Track redaction metadata          │ │
    │  └─────────────────────────────────────┘ │
    │                                           │
    │  ┌─────────────────────────────────────┐ │
    │  │ RetentionPolicyService (Scenario B) │ │
    │  │ - Soft-archive old events           │ │
    │  │ - Maintain chain integrity          │ │
    │  └─────────────────────────────────────┘ │
    │                                           │
    │  ┌─────────────────────────────────────┐ │
    │  │ ComplianceReportingService (Scenario C)
    │  │ - Compliance reports                │ │
    │  │ - Audit trails                      │ │
    │  │ - Access tracking                   │ │
    │  └─────────────────────────────────────┘ │
    │                                           │
    │  ┌─────────────────────────────────────┐ │
    │  │ ExportService (Scenario B)          │ │
    │  │ - JSON export                       │ │
    │  │ - CSV export                        │ │
    │  │ - Bulk data export with proof       │ │
    │  └─────────────────────────────────────┘ │
    └───────────────┬────────────────────────────┘
                    │
    ┌───────────────▼────────────────────────────┐
    │    Repository Layer (Spring Data JPA)      │
    │  - AuditEventRepository                    │
    │  - UserRepository                          │
    │  - RetentionPolicyRepository               │
    │  - AuditEventRedactionRepository           │
    └───────────────┬────────────────────────────┘
                    │
    ┌───────────────▼────────────────────────────┐
    │      H2 In-Memory Database (Dev/Test)      │
    │      PostgreSQL (Production)               │
    └────────────────────────────────────────────┘
```

---

## Data Model

### Core Entity: AuditEvent

```
┌─────────────────────────────────────────────┐
│           AUDIT_EVENTS Table                │
├─────────────────────────────────────────────┤
│ PK id                    BIGINT              │
│ FN eventType             VARCHAR(100)        │ <- What happened
│ FN actorId               VARCHAR(255)        │ <- Who did it
│ FN resourceType          VARCHAR(100)        │ <- Resource type
│ FN resourceId            VARCHAR(255)        │ <- Which resource
│    payload               CLOB                │ <- JSON details
│ FN timestamp             DATETIME            │ <- When
│    createdAt             DATETIME            │ <- DB created time
│    updatedAt             DATETIME            │ <- DB modified time
│    archivedAt            DATETIME (NULL)     │ <- Archive timestamp
│ UQ contentHash           VARCHAR(64)         │ <- SHA-256 of content
│    previousHash          VARCHAR(64)         │ <- Previous event's hash
│ UQ chainPosition         BIGINT              │ <- Sequential position
│    archived              BOOLEAN             │ <- Is archived (soft-delete)
│    redactionMetadata     CLOB (NULL)         │ <- JSON redaction tracking
│    createdBy             VARCHAR(255)        │ <- Audit: who created
│    updatedBy             VARCHAR(255)        │ <- Audit: who last modified
└─────────────────────────────────────────────┘

Indexes (6 total for query performance):
- idx_audit_events_chain_position (chain_position) UNIQUE
- idx_audit_events_actor_id (actor_id)
- idx_audit_events_resource (resource_type, resource_id)
- idx_audit_events_event_type (event_type)
- idx_audit_events_timestamp (timestamp)
- idx_audit_events_archived (archived)
```

### Hash Chain Structure

```
Event 1 (Genesis)
├─ id: 1
├─ chainPosition: 1
├─ contentHash: A1B2C3... (SHA-256 of content)
├─ previousHash: GENESIS_HASH
└─ eventType: USER_LOGIN, actorId: alice, timestamp: 2026-08-20T10:00:00

Event 2
├─ id: 2
├─ chainPosition: 2
├─ contentHash: D4E5F6... (SHA-256 of content)
├─ previousHash: A1B2C3... (references Event 1's contentHash)
└─ eventType: RECORD_UPDATED, actorId: bob, timestamp: 2026-08-20T10:05:00

Event 3
├─ id: 3
├─ chainPosition: 3
├─ contentHash: G7H8I9... (SHA-256 of content)
├─ previousHash: D4E5F6... (references Event 2's contentHash)
└─ eventType: PERMISSION_GRANTED, actorId: admin, timestamp: 2026-08-20T10:10:00

Verification Logic:
✓ Event 1: previousHash == GENESIS_HASH
✓ Event 2: previousHash == Event1.contentHash
✓ Event 3: previousHash == Event2.contentHash
✓ All chainPositions sequential (1, 2, 3, ...)
```

### Supporting Entities

**AuditEventRedaction** (Scenario B - Redaction)
```
├─ auditEventId (FK to AuditEvent)
├─ redactedFields (JSON array of field names)
├─ redactionReason (enum: PII_REMOVAL, COMPLIANCE_GDPR, etc.)
├─ redactedBy (user ID)
├─ redactedAt (timestamp)
└─ Note: Original AuditEvent.contentHash UNCHANGED
```

**RetentionPolicy** (Scenario B - Archival)
```
├─ policyId
├─ retentionDays (configurable, default 365)
├─ archiveSchedule (cron expression)
├─ Note: Events archived via AuditEvent.markAsArchived()
```

**User** (Authentication)
```
├─ userId
├─ username (unique)
├─ passwordHash (BCrypt, strength 12)
├─ role (AUDIT_WRITER, AUDITOR, ADMIN)
└─ createdAt
```

---

## Hash Chain Design

### Why SHA-256?

1. **FIPS 140-2 approved**: Government/regulatory compliance
2. **256-bit output (64 hex chars)**: Collision-resistant
3. **Fast enough**: O(1) computation per event
4. **Industry standard**: Used in blockchain, certificates
5. **No dependency on time**: Deterministic (same input = same hash)

### Why Chain Previous Hash?

**Immutability guarantee**: Modifying ANY event invalidates all subsequent hashes

Example of tampering detection:
```
Original Chain:
Event 1: contentHash = AAAA, previousHash = GENESIS
Event 2: contentHash = BBBB, previousHash = AAAA
Event 3: contentHash = CCCC, previousHash = BBBB

Attacker modifies Event 1's payload:
Event 1: contentHash = AAAA' (now invalid!), previousHash = GENESIS
Event 2: contentHash = BBBB (now invalid, because previousHash != AAAA'), previousHash = AAAA
Event 3: contentHash = CCCC (still points to BBBB, which is now invalid)

Verification fails at Event 2:
✗ Event2.previousHash (AAAA) != Event1.contentHash (AAAA') → TAMPERING DETECTED
```

### Timestamp in Hash

```
Hash Content Format:
eventType|actorId|resourceType|resourceId|payload|timestamp

Critical: Timestamp must be resolved ONCE before hashing
Good:
  ts = LocalDateTime.now()
  hash = sha256(eventType|actor|...|ts)
  event.timestamp = ts
  event.hash = hash

Bad (creates unverifiable events):
  hash = sha256(eventType|actor|...|LocalDateTime.now())  // time 1
  event.timestamp = LocalDateTime.now()  // time 2 (different!)
  event.hash = hash (now unverifiable!)
```

---

## API Design

### Authentication Pattern

All endpoints (except `/auth/login`, `/auth/register`) require JWT token:

```bash
POST /api/v1/auth/login
Request:  { "username": "alice", "password": "secret" }
Response: { "token": "eyJhbGc...", "tokenType": "Bearer", "expiresIn": 86400, "role": "AUDITOR" }

All subsequent requests:
Header: Authorization: Bearer eyJhbGc...
```

### Scenario A: Core API

| Operation | Endpoint | Method | Role | Status Code |
|-----------|----------|--------|------|-------------|
| Create event | `/api/v1/audit/events` | POST | AUDIT_WRITER, ADMIN | 201 |
| List events | `/api/v1/audit/events` | GET | AUDITOR, ADMIN | 200 |
| Get event details | `/api/v1/audit/events/{id}` | GET | AUDITOR, ADMIN | 200 |
| Search by actor | `/api/v1/audit/events/search/by-actor?actorId=X` | GET | AUDITOR, ADMIN | 200 |
| Search by resource | `/api/v1/audit/events/search/by-resource?resourceType=X&resourceId=Y` | GET | AUDITOR, ADMIN | 200 |
| Search by type | `/api/v1/audit/events/search/by-type?eventType=X` | GET | AUDITOR, ADMIN | 200 |
| Search by time range | `/api/v1/audit/events/search/by-time-range?startTime=X&endTime=Y` | GET | AUDITOR, ADMIN | 200 |
| Verify full chain | `/api/v1/audit/events/verify-chain` | POST | AUDITOR, ADMIN | 200 |
| Verify event | `/api/v1/audit/events/{id}/verify` | POST | AUDITOR, ADMIN | 200 |
| Get statistics | `/api/v1/audit/events/stats/summary` | GET | AUDITOR, ADMIN | 200 |

### Scenario B: Extension API

| Operation | Endpoint | Method | Role | Status Code |
|-----------|----------|--------|------|-------------|
| Apply retention | `/api/v1/compliance/retention/apply?retentionDays=365` | POST | ADMIN | 200 |
| Redact event | `/api/v1/compliance/redact/{eventId}` | POST | ADMIN | 200 |
| Export JSON | `/api/v1/compliance/export/json` | GET | ADMIN | 200 |
| Export CSV | `/api/v1/compliance/export/csv` | GET | ADMIN | 200 |
| Archive event | `/api/v1/audit/events/{id}/archive` | POST | ADMIN | 200 |

### Scenario C: Compliance Reporting API

| Operation | Endpoint | Method | Role | Status Code |
|-----------|----------|--------|------|-------------|
| Compliance report | `/api/v1/compliance/reports/compliance?startDate=X&endDate=Y` | GET | ADMIN | 200 |
| User audit trail | `/api/v1/compliance/reports/user-audit-trail?actorId=X&days=30` | GET | ADMIN | 200 |
| Resource audit trail | `/api/v1/compliance/reports/resource-audit-trail?resourceType=X&resourceId=Y` | GET | ADMIN | 200 |
| Compliance check | `/api/v1/compliance/check` | POST | ADMIN | 200 |
| Full audit report | `/api/v1/compliance/reports/audit` | GET | ADMIN | 200 |

---

## Key Design Decisions

### 1. Append-Only Semantics
**Decision**: No UPDATE or DELETE endpoints after creation  
**Rationale**: Tamper-evident guarantee requires immutability  
**Trade-off**: Cannot fix data entry errors (only redaction option)

### 2. Soft-Archive vs. Hard-Delete
**Decision**: Mark `archived=true` instead of deleting  
**Rationale**: Preserves chain integrity, satisfies retention policy  
**Trade-off**: Storage overhead, need to filter archived in queries

### 3. Redaction Strategy (Immutable Record Pattern)
**Decision**: Original hash unchanged, separate redaction records  
**Rationale**: Satisfies both tamper-evidence AND data privacy  
**Trade-off**: Redaction metadata itself could be sensitive, requires access control

### 4. Single Global Chain
**Decision**: One sequential chain for all events  
**Rationale**: Simplicity, single source of truth  
**Trade-off**: O(n) verification cost as chain grows (see scalability risks)

### 5. User-Supplied vs. Server-Assigned Timestamp
**Decision**: Accept caller's timestamp if provided, else use server time  
**Rationale**: Flexibility for audit events from other systems  
**Trade-off**: Requires careful handling to avoid time-travel exploits

### 6. JWT-Only Authentication
**Decision**: Stateless JWT tokens, no server-side sessions  
**Rationale**: Scalable, cloud-friendly, no session storage needed  
**Trade-off**: Cannot revoke tokens before expiry (use rotation instead)

---

## Performance Characteristics

| Operation | Complexity | Notes |
|-----------|-----------|-------|
| Create event | O(1) | Hash computation + DB insert |
| Query by filter | O(log n) | Database index used |
| List events (paginated) | O(k) | k = page size (default 20) |
| Verify full chain | O(n) | Walks all events sequentially |
| Verify single event | O(1) | Direct lookup + hash comparison |
| Archive event | O(1) | Single update |
| Redact event | O(1) | Separate insert + metadata update |

**Scalability Issue**: O(n) chain verification becomes slow at >1M events  
**Mitigation**: Add periodic snapshots, verify only from snapshot onwards

---

## Limitations & Assumptions

### Limitations
1. **Single sequential chain**: Cannot partition for horizontal scaling
2. **No time-based ordering**: Chain position is absolute, not time-based
3. **O(n) verification**: Full chain walk expensive for large datasets
4. **Timestamp uniqueness**: No guarantee of unique timestamp per event
5. **No event deletion**: Only archive available
6. **Redaction metadata retention**: Redaction records also stored (not deleted)
7. **No sharding**: All events in one table

### Assumptions
1. **Append-only operations**: No concurrent writes to same position
2. **Local timezone**: All timestamps treated as application timezone
3. **SHA-256 sufficient**: No quantum-resistant algorithms needed (yet)
4. **No external clock sync**: Server time source is reliable
5. **Database integrity**: Underlying DB transaction log not tampered with
6. **Single-node deployment**: No multi-node cluster consensus needed

---

## Security Considerations

### Threats Mitigated
| Threat | Mitigation | Residual Risk |
|--------|-----------|----------------|
| Event tampering | Hash chain verification | Entire DB tampered |
| Unauthorized access | JWT + role-based access control | Compromised JWT secret |
| Privilege escalation | @PreAuthorize on all endpoints | Code vulnerability |
| SQL injection | Parameterized JPA queries | Logic bugs |
| Credential exposure | BCrypt (strength 12) | Weak passwords |
| Data eavesdropping | HTTPS/TLS required (not in app) | Network layer concern |

### Production Hardening Needed
1. **TLS/HTTPS**: Configure reverse proxy (nginx, load balancer)
2. **H2 console**: Disable in production (`spring.h2.console.enabled=false`)
3. **JWT secret**: Use 32+ char random string, rotate regularly
4. **Password policy**: Enforce complexity for user passwords
5. **API rate limiting**: Add at gateway level (not in service)
6. **Audit logging**: Log all sensitive operations to syslog
7. **Database backups**: Regular point-in-time recovery strategy

---

## Testing & Validation

### Chain Tampering Validation
```
Test Procedure:
1. Create 3+ events via POST /api/v1/audit/events
2. Verify chain: POST /api/v1/audit/events/verify-chain → valid: true
3. Tamper: Directly modify event 2's payload in database
4. Verify chain: POST /api/v1/audit/events/verify-chain → valid: false
5. Confirm first breach at position 2 (or 3, depending on detection logic)
```

### Integration Test Coverage
- Unit tests: Service layer logic
- Integration tests: Controller + Service + Repository + Database
- Contract tests: REST API responses against OpenAPI spec
- Security tests: JWT validation, role enforcement

---

## Deployment & Operations

### Local Development (H2)
```powershell
mvn spring-boot:run
# Runs on http://localhost:8282
# H2 console at http://localhost:8282/h2-console
```

### Production (PostgreSQL)
1. Update `application-prod.properties`:
   ```ini
   spring.datasource.url=jdbc:postgresql://db-host:5432/audit_log
   spring.datasource.username=${DB_USER}
   spring.datasource.password=${DB_PASSWORD}
   spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.PostgreSQLDialect
   ```

2. Run database migrations:
   ```bash
   mvn flyway:migrate
   ```

3. Deploy JAR:
   ```bash
   java -jar audit-log-service-1.0.0.jar
   ```

### Operational Checklists
- [ ] Verify chain integrity daily: `POST /api/v1/audit/events/verify-chain`
- [ ] Monitor response times: Chain verification should complete in <5sec for <1M events
- [ ] Apply retention policy: `POST /api/v1/compliance/retention/apply?retentionDays=365`
- [ ] Export compliance reports: `GET /api/v1/compliance/reports/compliance`
- [ ] Rotate JWT secrets: Every 90 days
- [ ] Database backups: Daily, with test restores


