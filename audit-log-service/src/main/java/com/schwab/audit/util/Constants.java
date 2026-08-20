package com.schwab.audit.util;

/**
 * Application-wide constants for the Audit Log Service.
 */
public final class Constants {

    // Cryptography
    public static final String HASH_ALGORITHM = "SHA-256";
    public static final String GENESIS_HASH = "GENESIS_HASH";
    public static final int HASH_HEX_LENGTH = 64;

    // Validation
    public static final int EVENT_TYPE_MAX_LENGTH = 100;
    public static final int ACTOR_ID_MAX_LENGTH = 255;
    public static final int RESOURCE_TYPE_MAX_LENGTH = 100;
    public static final int RESOURCE_ID_MAX_LENGTH = 255;
    public static final String EVENT_TYPE_PATTERN = "^[A-Z_]{1,100}$";

    // Pagination
    public static final int PAGINATION_DEFAULT_SIZE = 20;
    public static final int PAGINATION_MAX_SIZE = 100;
    public static final int PAGINATION_MIN_SIZE = 1;

    // JWT
    public static final String JWT_BEARER_PREFIX = "Bearer ";
    public static final String JWT_CLAIM_ROLE = "role";
    public static final String JWT_CLAIM_SUB = "sub";

    // HTTP Headers
    public static final String HEADER_AUTHORIZATION = "Authorization";
    public static final String HEADER_CONTENT_DISPOSITION = "Content-Disposition";

    // Role-Based Access Control
    public static final String ROLE_AUDIT_WRITER = "AUDIT_WRITER";
    public static final String ROLE_AUDITOR = "AUDITOR";
    public static final String ROLE_ADMIN = "ADMIN";

    // Database
    public static final int CONTENT_HASH_COLUMN_LENGTH = 64;

    // Event Types (Common Examples)
    public static final String EVENT_TYPE_USER_LOGIN = "USER_LOGIN";
    public static final String EVENT_TYPE_USER_LOGOUT = "USER_LOGOUT";
    public static final String EVENT_TYPE_RECORD_UPDATED = "RECORD_UPDATED";
    public static final String EVENT_TYPE_RECORD_DELETED = "RECORD_DELETED";
    public static final String EVENT_TYPE_PERMISSION_GRANTED = "PERMISSION_GRANTED";
    public static final String EVENT_TYPE_PERMISSION_REVOKED = "PERMISSION_REVOKED";

    // Redaction Event Types
    public static final String EVENT_TYPE_FIELD_REDACTED = "FIELD_REDACTED";
    public static final String EVENT_TYPE_RECORD_ARCHIVED = "RECORD_ARCHIVED";

    // Resource Types (Common Examples)
    public static final String RESOURCE_TYPE_ACCOUNT = "ACCOUNT";
    public static final String RESOURCE_TYPE_USER = "USER";
    public static final String RESOURCE_TYPE_USER_SESSION = "USER_SESSION";

    private Constants() {
        // Prevent instantiation
        throw new UnsupportedOperationException("Constants class cannot be instantiated");
    }
}
