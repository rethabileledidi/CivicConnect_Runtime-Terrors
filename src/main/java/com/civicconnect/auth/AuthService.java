package com.civicconnect.auth;

import com.civicconnect.common.ApiException;
import com.civicconnect.common.Validator;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Locale;

/**
 * Registration and sign-in (FR-12 / NFR-02 / NFR-10).
 * <ul>
 *   <li>Passwords: 15-128 characters, no composition rules (NIST SP 800-63B-4 single-factor minimum).</li>
 *   <li>Lockout: 5 consecutive failures lock the account for 15 minutes; lock state is stored in the
 *       database so it survives restarts.</li>
 *   <li>One generic message for "unknown email" and "wrong password", so the API cannot be used
 *       to discover which emails are registered.</li>
 *   <li>Public registration always creates a RESIDENT. Staff, coordinator and manager accounts are
 *       created by an administrator (deliberately not self-service).</li>
 * </ul>
 */
public final class AuthService {

    public static final int MIN_PASSWORD = 15;
    public static final int MAX_PASSWORD = 128;
    public static final int MAX_FAILURES = 5;
    public static final Duration LOCK_DURATION = Duration.ofMinutes(15);
    static final String GENERIC_FAILURE = "Incorrect email or password.";

    private final UserRepository users;
    private final PasswordHasher hasher;
    private final Clock clock;

    public AuthService(UserRepository users, PasswordHasher hasher, Clock clock) {
        this.users = users;
        this.hasher = hasher;
        this.clock = clock;
    }

    public record Registration(String fullName, String email, String phone, String municipality,
                               String password, String confirmPassword) { }

    public AuthenticatedUser register(Registration r) {
        String email = Validator.clean(r.email()).toLowerCase(Locale.ROOT);
        String password = r.password() == null ? "" : r.password();
        new Validator()
                .length("fullName", r.fullName(), 3, 120, "Full name")
                .matches("email", email, Validator.EMAIL, "Enter a valid email address.")
                .require(email.length() <= 254, "email", "Email is too long.")
                .matches("phone", r.phone(), Validator.SA_PHONE, "Enter a 10-digit SA number, e.g. 0821234567.")
                .length("municipality", r.municipality(), 2, 100, "Municipality")
                .require(password.length() >= MIN_PASSWORD, "password",
                        "Password must be at least " + MIN_PASSWORD + " characters. A short sentence works well.")
                .require(password.length() <= MAX_PASSWORD, "password", "Password is too long.")
                .require(password.equals(r.confirmPassword()), "confirm", "Passwords do not match.")
                .throwIfInvalid();

        String hash = hasher.hash(password.toCharArray());
        try {
            UserAccount created = users.createResident(email, Validator.clean(r.fullName()),
                    Validator.clean(r.phone()), Validator.clean(r.municipality()), hash);
            return created.toAuthenticated();
        } catch (UserRepository.DuplicateEmail e) {
            throw new ApiException.Conflict("EMAIL_TAKEN", "An account with this email already exists.");
        }
    }

    public AuthenticatedUser login(String rawEmail, String rawPassword) {
        String email = Validator.clean(rawEmail).toLowerCase(Locale.ROOT);
        char[] password = rawPassword == null ? new char[0] : rawPassword.toCharArray();
        Instant now = clock.instant();

        UserAccount account = users.findByEmail(email).orElse(null);
        if (account == null || !account.active() || account.passwordHash() == null) {
            if (password.length > 0) hasher.dummyVerify(password);   // similar response time either way
            throw new ApiException.Unauthenticated(GENERIC_FAILURE);
        }
        if (account.isLockedAt(now)) {
            throw new ApiException.Locked("Too many failed attempts. Try again after 15 minutes.");
        }
        boolean ok = password.length > 0 && hasher.verify(password, account.passwordHash());
        if (!ok) {
            // A lock that has expired starts a fresh count.
            int previous = account.lockedUntil() != null && !account.isLockedAt(now) ? 0 : account.failedLoginCount();
            int failures = previous + 1;
            Instant lockUntil = failures >= MAX_FAILURES ? now.plus(LOCK_DURATION) : null;
            users.recordFailedLogin(account.userId(), lockUntil != null ? 0 : failures, lockUntil);
            if (lockUntil != null) {
                throw new ApiException.Locked("Too many failed attempts. Try again after 15 minutes.");
            }
            throw new ApiException.Unauthenticated(GENERIC_FAILURE);
        }
        users.recordSuccessfulLogin(account.userId(), now);
        return account.toAuthenticated();
    }
}
