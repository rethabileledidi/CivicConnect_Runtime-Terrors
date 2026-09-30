package com.civicconnect.service;

import com.civicconnect.auth.AuthenticatedUser;
import com.civicconnect.auth.UserRole;
import com.civicconnect.data.model.ServiceRequestRecord;

/**
 * Who may see which request (FR-12 / NFR-02; OWASP Top 10 A01 Broken Access Control).
 * Deny by default: a role not listed here sees nothing.
 */
public final class AccessPolicy {

    private AccessPolicy() { }

    public static boolean canView(AuthenticatedUser user, ServiceRequestRecord r) {
        if (user == null) return false;
        return switch (user.role()) {
            case RESIDENT -> r.requesterId() == user.userId();
            case STAFF -> (r.assigneeId() != null && r.assigneeId() == user.userId()) || r.requesterId() == user.userId();
            case COORDINATOR, MANAGER, ADMIN -> true;
        };
    }

    /** Only staff and oversight roles have a work queue. */
    public static boolean hasQueue(AuthenticatedUser user) {
        return user != null && (user.role() == UserRole.STAFF || user.role().isOversight());
    }
}
