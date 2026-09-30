package com.civicconnect.auth;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Persistence port for user accounts. The JDBC version talks to PostgreSQL; unit tests
 * use an in-memory fake, so AuthService's rules are tested without a database.
 */
public interface UserRepository {

    Optional<UserAccount> findByEmail(String email);

    Optional<UserAccount> findById(long userId);

    /** Creates a RESIDENT account and returns it. Throws DuplicateEmail if the email exists. */
    UserAccount createResident(String email, String fullName, String phone, String municipality, String passwordHash);

    /** Stores the new failure count and (optionally) a lock expiry. */
    void recordFailedLogin(long userId, int failedLoginCount, Instant lockedUntil);

    /** Clears failures and lock, and stamps the successful sign-in time. */
    void recordSuccessfulLogin(long userId, Instant at);

    /** Active STAFF users, for the coordinator's "assign to" list. */
    List<UserAccount> findActiveStaff();

    /** Thrown when the case-insensitive unique email index rejects an insert. */
    final class DuplicateEmail extends RuntimeException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
        public DuplicateEmail() { super("Email already registered"); }
    }
}
