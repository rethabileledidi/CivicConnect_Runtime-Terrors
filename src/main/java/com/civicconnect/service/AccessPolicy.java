package com.civicconnect.service;

import com.civicconnect.auth.AuthenticatedUser;
import com.civicconnect.auth.UserRole;
import com.civicconnect.data.model.ServiceRequestRecord;

/**
 * Who may see which request (FR-12 / NFR-02; OWASP Top 10 A01 Broken Access Control).
 * Deny by default: a role not listed here sees nothing.
 * <p>
 * Restricted categories (V7, e.g. safety and security concerns) are hidden from coordinators:
 * only the requester, the assigned staff member, managers and admins may see them.
 */
public final class AccessPolicy {

    private AccessPolicy() { }

    /** @param restricted true if the request's category is restricted (FR-12) */
    public static boolean canView(AuthenticatedUser user, ServiceRequestRecord r, boolean restricted) {
        if (user == null) return false;
        return switch (user.role()) {
            case RESIDENT -> r.requesterId() == user.userId();
            case STAFF -> isAssignee(user, r) || r.requesterId() == user.userId();
            case COORDINATOR -> !restricted;
            case MANAGER, ADMIN -> true;
        };
    }

    /** Staff queues are already limited to their own assignments, so only coordinators lose rows. */
    public static boolean queueIncludesRestricted(AuthenticatedUser user) {
        return user.role() != UserRole.COORDINATOR;
    }

    /** Only staff and oversight roles have a work queue. */
    public static boolean hasQueue(AuthenticatedUser user) {
        return user != null && (user.role() == UserRole.STAFF || user.role().isOversight());
    }

    /**
     * FR-09: staff comments are internal working notes. The assigned staff member and oversight
     * roles may read and add them; residents may do neither. Call only after {@link #canView} passed.
     */
    public static boolean canComment(AuthenticatedUser user, ServiceRequestRecord r) {
        if (user == null) return false;
        return switch (user.role()) {
            case STAFF -> isAssignee(user, r);
            case COORDINATOR, MANAGER, ADMIN -> true;
            case RESIDENT -> false;
        };
    }

    private static boolean isAssignee(AuthenticatedUser user, ServiceRequestRecord r) {
        return r.assigneeId() != null && r.assigneeId() == user.userId();
    }
}
