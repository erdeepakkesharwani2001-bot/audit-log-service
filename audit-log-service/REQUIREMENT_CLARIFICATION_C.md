# Scenario C: Requirement Clarification - Compliance Reporting

## Original Ambiguous Requirement

**From Assignment**:
> "Product says: 'Regulators need to be able to audit access to client account data.'"

This requirement is intentionally under-specified and contains multiple ambiguities that must be resolved before implementation.

---

## Ambiguities Identified

### 1. **Scope of "Access to Client Account Data"**

**Ambiguity**: What exactly constitutes "access"?

**Possible interpretations**:
- (a) **Who can access accounts**: Permission/role-based audit (who is authorized)
- (b) **Who actually accessed accounts**: Activity-based audit (who really opened/read the data)
- (c) **Both**: Complete picture of authorization + actual access
- (d) **Metadata only**: Just the fact that access occurred, without full event details

**Questions to Ask**:
- Do regulators want to know who CAN access, or who DID access?
- Should we log every view, or just modifications?
- Is export/download activity audited separately?
- Do system service accounts count (e.g., batch processors)?

**Assumption Made**: Interpreted as **Activity-based audit** - tracking actual access events (who accessed which resource, when, what operation). This is more stringent and useful for regulators than permission-only tracking.

---

### 2. **What Data Sources Constitute "Access"?**

**Ambiguity**: Which operations should be audited?

**Possible data sources**:
- (a) **API calls only**: REST endpoints hit, tracked at application layer
- (b) **Database queries**: Direct DB access, tracked at DB layer
- (c) **UI views**: Frontend navigation/clicks
- (d) **Exports**: Download operations
- (e) **All of the above**: Comprehensive tracking
- (f) **Only authenticated operations**: Exclude failed/denied attempts

**Questions to Ask**:
- Should we track failed access attempts (security events)?
- Do data export operations count as "access"?
- Should we track anonymous/unauthenticated requests?
- Is read-only access the concern, or also modifications?

**Assumption Made**: Implemented **API-level tracking** of authenticated operations (successful and failed). This includes:
- Read operations (queries)
- Modification operations (creates, updates via redaction/archive)
- Export operations
- Excludes: Unauthenticated attempts (logged separately at security layer)

---

### 3. **Time-Based Retention & Reporting Window**

**Ambiguity**: How long must audit data be retained and reportable?

**Possible retention models**:
- (a) **Rolling window**: Last N days (e.g., 90-day rolling)
- (b) **Fixed period**: Calendar-based (e.g., calendar year)
- (c) **Compliance-based**: SOC 2 (1 year), HIPAA (6 years), GDPR (varies)
- (d) **No deletion**: Indefinite retention (append-only forever)
- (e) **Tiered**: Hot data (1 year), cold archive (7 years)

**Questions to Ask**:
- Which regulation applies? (SOC 2, HIPAA, GDPR, PCI-DSS?)
- What's the minimum retention window?
- Can archived events be deleted after N years?
- Should retention be immutable after cutoff?

**Assumption Made**: Implemented **365-day default retention** with configurable archival:
- Active audit events: Last 365 days (hot)
- Older events: Archived (soft-delete, searchable)
- Archive is immutable: Cannot unarchive

**Endpoint**: `POST /api/v1/compliance/retention/apply?retentionDays=365`

---

### 4. **Report Format & Consumers**

**Ambiguity**: Who consumes the report, and what format do they need?

**Possible formats**:
- (a) **JSON API**: Structured, machine-readable (for systems)
- (b) **CSV export**: Spreadsheet-friendly (for analysts)
- (c) **PDF report**: Human-readable with charts/summaries (for management)
- (d) **Real-time dashboard**: Live query interface (for monitoring)
- (e) **Compliance bundle**: Signed/tamper-proof package (for regulators)

**Possible consumers**:
- (a) **Compliance team**: Internal policy enforcement
- (b) **External auditors**: SOC 2, HIPAA audit
- (c) **Regulators**: Government agencies (SEC, FINRA, OCC)
- (d) **Security team**: Incident investigation
- (e) **System operators**: Daily operational monitoring

**Questions to Ask**:
- Who will consume the report (internal/external)?
- What's the primary use case (investigation, compliance, monitoring)?
- Does the report need digital signature (tamper-proof)?
- Is real-time dashboard needed or offline report sufficient?

**Assumption Made**: Implemented **API endpoints + JSON export**:
- Real-time read access via REST API
- Bulk JSON/CSV export for compliance bundles
- No PDF generation (reports assembled by client)
- Reports include chain verification proof (tamper-proof)

**Endpoints**:
- `GET /api/v1/compliance/reports/compliance?startDate=X&endDate=Y`
- `GET /api/v1/compliance/reports/user-audit-trail?actorId=X&days=30`
- `GET /api/v1/compliance/reports/resource-audit-trail?resourceType=X&resourceId=Y`
- `GET /api/v1/compliance/export/json`
- `GET /api/v1/compliance/export/csv`

---

### 5. **Failed vs. Successful Access Attempts**

**Ambiguity**: Should we track failed access attempts?

**Possible strategies**:
- (a) **Successful only**: Only log when access granted/data returned
- (b) **Failed only**: Log denied/unauthorized attempts separately
- (c) **Both**: Comprehensive security audit trail
- (d) **By role**: Different policies for different roles

**Questions to Ask**:
- Is failed access important for compliance (breach investigation)?
- Should failed attempts be in same audit log or separate security log?
- Do we care about authentication failures (bad password)?
- What about authorization failures (insufficient role)?

**Assumption Made**: Implemented **Successful operations only** in main audit log:
- Successful authentication + authorization flows
- Failed operations tracked at Spring Security level (separate logs)
- Can be enhanced by adding `status: SUCCESS|FAILED` field to audit event

---

### 6. **System Account vs. User Account Access**

**Ambiguity**: Should system/service accounts be audited?

**Possible policies**:
- (a) **User accounts only**: Exclude system accounts, batch jobs
- (b) **All accounts**: Include system, scheduled, service accounts
- (c) **Configurable**: Allow filtering by account type
- (d) **Separate tracking**: Different audit trails for users vs. systems

**Questions to Ask**:
- Do regulators care about system access (batch processes)?
- How do we distinguish system from user accounts?
- Should service-to-service calls be tracked?
- Is there a whitelist of "trusted" system accounts?

**Assumption Made**: Implemented **All actors tracked**, with ability to filter:
- Users, services, batch jobs all create events
- `actorId` field identifies source (can include service names)
- Filtering by actor role at query time: `/search/by-actor?actorId=system_account&exclude=true`

---

### 7. **Data Granularity: Event Level vs. Summary Level**

**Ambiguity**: How detailed should audit entries be?

**Possible granularity levels**:
- (a) **Summary**: "User alice accessed account ACC-001"
- (b) **Detailed**: Summary + timestamp + IP address + user agent
- (c) **Full context**: Detailed + request body + response status
- (d) **Audit trail**: Sequence of changes to resource

**Questions to Ask**:
- Do we need IP address, geolocation for compliance?
- Should response status (200, 403, 500) be logged?
- How much payload detail is needed?
- Is PII sensitive enough to redact in audit log?

**Assumption Made**: Implemented **Event-level tracking**:
- Core fields: actorId, resourceType, resourceId, eventType, timestamp, payload
- Payload is JSON (flexible for event-specific details)
- IP/user-agent NOT captured (client responsibility)
- Sensitive fields redactable via `/api/v1/compliance/redact/{eventId}`

---

## Clarified Requirement Statement

Based on the above analysis, here is the clarified requirement implemented in this service:

### **Compliance Reporting Requirement (Clarified)**

**Purpose**: Provide regulators and auditors with a verifiable, immutable audit trail of who accessed what resources when, enabling compliance with regulatory audit requirements.

**Scope**:
- Track all authenticated API operations (successful access attempts)
- Cover all resource types and actors
- Retain audit events for 365 days (configurable)
- Archive older events (soft-delete, maintains chain integrity)
- Support sensitive data redaction while preserving tamper-evidence

**Key Capabilities**:
1. **Real-time compliance reporting**: Query audit events by date range, actor, resource, event type
2. **User audit trails**: All operations performed by a specific user
3. **Resource audit trails**: All access to a specific resource
4. **Compliance checks**: Validation that audit log is intact (no tampering detected)
5. **Data export**: Bulk export for external audit in JSON/CSV format with chain verification proof

**Access Control**:
- Only ADMIN role can access compliance endpoints
- No privilege escalation (roles enforced at all layers)

**Integrity Guarantees**:
- Append-only: No update/delete after creation
- Tamper-detectable: SHA-256 hash chain with previous-hash linking
- Verifiable: `POST /api/v1/audit/events/verify-chain` confirms chain integrity
- Archive immutable: Once archived, cannot be unarchived

**Out of Scope** (explicitly NOT implementing):
- Real-time alerting on access events
- Automatic remediation/policy enforcement
- IP address, geolocation tracking
- Field-level access granularity (logged at operation level, not field level)
- PDFs or complex report formatting (API provides data, client formats as needed)
- Multi-tenancy isolation (single shared audit log for all resources)

---

## Design Decisions & Rationale

### Decision 1: Implement as REST API Endpoints

**Design**: Expose compliance functionality via REST endpoints rather than scheduled batch reports.

**Rationale**:
- Flexibility: Auditors can query specific date ranges on-demand
- Real-time: No delay waiting for batch job
- Scalable: Each query is independent
- Auditable: Query attempts themselves are logged

**Trade-off**: 
- Real-time dashboard requires client UI implementation
- No built-in scheduling (client must call API periodically)

### Decision 2: Include Chain Verification in Export

**Design**: When exporting events, include chain segment verification proof (first/last hash, export signature).

**Rationale**:
- Recipient can independently verify data integrity
- Tamper-proof bundle for regulatory submission
- Proves no events were added/removed during export

**Trade-off**:
- Export payload slightly larger (includes metadata)
- Verification logic must be independently implemented by recipient

### Decision 3: Immutable Redaction Records

**Design**: When sensitive fields are redacted, create separate redaction record. Original audit event hash unchanged.

**Rationale**:
- Satisfies both data privacy (field masked) AND tamper-evidence (hash unchanged)
- Maintains audit trail of redactions (who, when, why)
- Complies with GDPR (redaction tracked) and regulatory requirements

**Trade-off**:
- Redaction metadata itself could be sensitive (restricted access needed)
- Schema complexity increased (separate table + metadata)

### Decision 4: 365-Day Default Retention

**Design**: Archive events older than 365 days by default; configurable via retention policy API.

**Rationale**:
- Aligns with SOC 2 Type II (1-year retention)
- Reasonable compromise between regulatory requirement and storage
- Can be overridden for HIPAA (6 years) or GDPR (data deletion)
- Archived events remain searchable (soft-delete)

**Trade-off**:
- Requires operational process to invoke archival policy
- No automatic cleanup (manual or scheduled job required)

### Decision 5: Role-Based Access (ADMIN Only)

**Design**: Compliance endpoints require ADMIN role; AUDITOR role is read-only for regular queries.

**Rationale**:
- Separation of duties: Operators vs. auditors
- Sensitive compliance data limited to administrators
- Easier to audit who accessed compliance reports

**Trade-off**:
- AUDITOR role cannot access redaction/retention endpoints
- May need to add intermediate role if delegation needed

---

## Implementation Details

### Compliance Endpoints Implemented

```
┌─ Scenario C: Compliance Reporting
│
├─ GET /api/v1/compliance/reports/compliance
│  Query: startDate, endDate
│  Response: Events within date range + summary statistics
│  Role: ADMIN
│
├─ GET /api/v1/compliance/reports/user-audit-trail
│  Query: actorId, days (default 30)
│  Response: All operations by specific user
│  Role: ADMIN
│
├─ GET /api/v1/compliance/reports/resource-audit-trail
│  Query: resourceType, resourceId
│  Response: All access to specific resource
│  Role: ADMIN
│
├─ POST /api/v1/compliance/check
│  Query: none
│  Response: Compliance status (chain integrity, event count)
│  Role: ADMIN
│
├─ GET /api/v1/compliance/export/json
│  Query: none (exports all events)
│  Response: JSON array + chain verification metadata
│  Role: ADMIN
│
└─ GET /api/v1/compliance/export/csv
   Query: none (exports all events)
   Response: CSV file + chain verification metadata
   Role: ADMIN
```

### Service Layer Implementation

**ComplianceReportingService** provides:
- `generateComplianceReport(startDate, endDate)` - Date range report
- `generateUserAuditTrail(actorId, days)` - Actor-specific report
- `generateResourceAuditTrail(resourceType, resourceId)` - Resource-specific report
- `performComplianceCheck()` - Integrity validation

**ExportService** provides:
- `exportAsJson()` - Full event export as JSON
- `exportAsCSV()` - Full event export as CSV
- `generateAuditReport()` - Formatted audit report

**RetentionPolicyService** provides:
- `archiveEventsOlderThan(cutoffDate)` - Soft-archive old events

**RedactionService** provides:
- `redactEvent(eventId, fields, reason, redactedBy)` - Field-level redaction

---

## What's Included vs. Excluded

| Feature | Included? | Rationale |
|---------|-----------|-----------|
| Date-range reporting | ✓ Yes | Core regulatory requirement |
| User audit trails | ✓ Yes | Track who accessed what |
| Resource audit trails | ✓ Yes | Track access to specific resources |
| Export JSON/CSV | ✓ Yes | Bulk data for external audit |
| Chain verification | ✓ Yes | Tamper-proof proof |
| Retention archival | ✓ Yes | Compliance retention windows |
| Field redaction | ✓ Yes | Data privacy (GDPR) |
| Real-time alerts | ✗ No | Out of scope (monitoring tool domain) |
| Automatic remediation | ✗ No | Out of scope (policy engine domain) |
| PDF reports | ✗ No | Out of scope (client formats JSON as needed) |
| IP/geolocation | ✗ No | Network layer concern |
| Multi-tenancy | ✗ No | Out of scope (single-tenant service) |

---

## Assumptions & Constraints

### Assumptions Made
1. **Regulators use standard APIs**: Assuming REST JSON format acceptable (not custom binary)
2. **Audit log immutability required**: No corrections/amendments after event creation
3. **Retention is configurable**: Different regulations need different windows
4. **Sensitive data redactable**: Some fields need masking for privacy
5. **Chain-wide verification sufficient**: No need for per-event digital signatures

### Regulatory Scope Assumed
- **Primary**: SOC 2 Type II (1-year audit trail required)
- **Secondary**: GDPR (data deletion/redaction), HIPAA (6-year retention)
- **Not assumed**: PCI-DSS, FedRAMP, NIST controls (can be added)

---

## Questions for Stakeholders

Before moving to production, these questions should be answered:

1. **Regulatory framework**: Which compliance standards apply (SOC 2, HIPAA, GDPR)?
2. **Retention window**: Should it be 1 year (SOC 2) or longer?
3. **Failed attempts**: Should we log failed access attempts separately?
4. **Export format**: Is JSON/CSV sufficient, or is PDF/XML needed?
5. **Verification proof**: Should exported data include digital signatures?
6. **Real-time alerts**: Will monitoring system need to subscribe to events?
7. **Field-level redaction**: Can entire operations be masked, or only specific fields?
8. **IP/location tracking**: Should geographic origin be audited?

---

## Future Enhancements

If requirements expand:
1. Add IP address / geolocation tracking
2. Implement real-time event streaming (Kafka)
3. Add automatic alerts on suspicious access patterns
4. Create PDF compliance reports
5. Support multi-tenant isolation (separate chains per tenant)
6. Implement cryptographic key rotation
7. Add machine-learning-based anomaly detection


