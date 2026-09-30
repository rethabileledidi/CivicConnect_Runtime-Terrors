package com.civicconnect.auth;

/** Role codes stored in civic.role (V3). */
public enum UserRole {
    RESIDENT, STAFF, COORDINATOR, MANAGER, ADMIN;

    /** Coordinators, managers and admins may see and triage every request. */
    public boolean isOversight() {
        return this == COORDINATOR || this == MANAGER || this == ADMIN;
    }

    public static UserRole fromCode(String code) {
        return valueOf(code.strip().toUpperCase());
    }
}
