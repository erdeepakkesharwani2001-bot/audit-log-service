package com.schwab.audit.entity.enums;

import org.springframework.security.core.GrantedAuthority;

/**
 * Enumeration of user roles for role-based access control (RBAC).
 * 
 * Roles:
 * - AUDIT_WRITER: Can create/write audit events
 * - AUDITOR: Can read/query audit events and verify chain
 * - ADMIN: Full administrative access (create, read, redact, archive, export)
 */
public enum UserRole implements GrantedAuthority {
    AUDIT_WRITER("ROLE_AUDIT_WRITER"),
    AUDITOR("ROLE_AUDITOR"),
    ADMIN("ROLE_ADMIN");

    private final String authority;

    UserRole(String authority) {
        this.authority = authority;
    }

    @Override
    public String getAuthority() {
        return this.authority;
    }

    /**
     * Parses a role string to UserRole enum.
     * Handles both formats: "ROLE_ADMIN" and "ADMIN"
     */
    public static UserRole fromString(String role) {
        if (role == null) {
            throw new IllegalArgumentException("Role cannot be null");
        }
        
        String cleaned = role.replace("ROLE_", "").toUpperCase();
        
        for (UserRole userRole : UserRole.values()) {
            if (userRole.name().equals(cleaned)) {
                return userRole;
            }
        }
        
        throw new IllegalArgumentException("Unknown role: " + role);
    }
}
