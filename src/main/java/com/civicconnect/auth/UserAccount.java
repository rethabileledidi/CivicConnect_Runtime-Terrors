package com.civicconnect.auth;

import java.time.Instant;

/** A row of civic.app_user as the auth module needs it (includes the password hash). */
public record UserAccount(long userId, String email, String fullName, String phone,
                          String municipality, UserRole role, Integer departmentId,
                          String passwordHash, boolean active,
                          int failedLoginCount, Instant lockedUntil) {

    public AuthenticatedUser toAuthenticated() {
        return new AuthenticatedUser(userId, email, fullName, role, phone, municipality);
    }

    public boolean isLockedAt(Instant now) {
        return lockedUntil != null && lockedUntil.isAfter(now);
    }
}
