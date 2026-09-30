package com.civicconnect.auth;

import com.civicconnect.common.ApiException;
import com.civicconnect.support.Fakes;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;

/** NFR-02 / NFR-10: password policy, generic errors, lockout, role on registration. */
class AuthServiceTest {

    private static final String GOOD = "correct horse battery staple";   // 28 chars, passes the 15 minimum
    private final PasswordHasher hasher = new PasswordHasher(1_000);     // fewer iterations only to keep tests fast
    private final Fakes.Users users = new Fakes.Users();

    private AuthService service(Clock clock) {
        return new AuthService(users, hasher, clock);
    }

    private AuthService.Registration reg(String email, String password, String confirm) {
        return new AuthService.Registration("Thandi Mokoena", email, "0821234567", "City of Tshwane", password, confirm);
    }

    @Test
    void registrationCreatesAResidentAndNeverStoresThePlainPassword() {
        AuthenticatedUser u = service(Fakes.fixedClock()).register(reg("Thandi@Example.com", GOOD, GOOD));
        assertEquals(UserRole.RESIDENT, u.role());
        assertEquals("thandi@example.com", u.email());
        String stored = users.findById(u.userId()).orElseThrow().passwordHash();
        assertFalse(stored.contains(GOOD));
        assertTrue(stored.startsWith("pbkdf2_sha256$1000$"));
    }

    @Test
    void shortPasswordsAndMismatchesAreRejectedWithFieldErrors() {
        ApiException.Validation e = assertThrows(ApiException.Validation.class,
                () -> service(Fakes.fixedClock()).register(reg("a@b.co", "only14chars!!!", "different")));
        assertTrue(e.fieldErrors().get("password").contains("15"));
        assertTrue(e.fieldErrors().containsKey("confirm"));
    }

    @Test
    void duplicateEmailIsAConflict() {
        AuthService s = service(Fakes.fixedClock());
        s.register(reg("dup@example.com", GOOD, GOOD));
        ApiException.Conflict e = assertThrows(ApiException.Conflict.class, () -> s.register(reg("DUP@example.com", GOOD, GOOD)));
        assertEquals("EMAIL_TAKEN", e.code());
    }

    @Test
    void unknownEmailAndWrongPasswordGiveTheSameMessage() {
        AuthService s = service(Fakes.fixedClock());
        s.register(reg("known@example.com", GOOD, GOOD));
        String unknown = assertThrows(ApiException.Unauthenticated.class, () -> s.login("nobody@example.com", GOOD)).getMessage();
        String wrong = assertThrows(ApiException.Unauthenticated.class, () -> s.login("known@example.com", "wrong password here")).getMessage();
        assertEquals(unknown, wrong);
    }

    @Test
    void fiveFailuresLockTheAccountForFifteenMinutes() {
        AuthService s = service(Fakes.fixedClock());
        long id = s.register(reg("lock@example.com", GOOD, GOOD)).userId();
        for (int i = 1; i <= 4; i++) {
            assertThrows(ApiException.Unauthenticated.class, () -> s.login("lock@example.com", "wrong password here"));
        }
        assertThrows(ApiException.Locked.class, () -> s.login("lock@example.com", "wrong password here"));
        // Even the right password is refused while locked
        assertThrows(ApiException.Locked.class, () -> s.login("lock@example.com", GOOD));
        assertEquals(Fakes.NOW.plus(AuthService.LOCK_DURATION), users.findById(id).orElseThrow().lockedUntil());

        // 16 minutes later the right password works and the counter resets
        AuthService later = service(Clock.offset(Fakes.fixedClock(), Duration.ofMinutes(16)));
        assertEquals(id, later.login("lock@example.com", GOOD).userId());
        assertEquals(0, users.findById(id).orElseThrow().failedLoginCount());
    }

    @Test
    void successfulLoginReturnsUserWithoutPasswordMaterial() {
        AuthService s = service(Fakes.fixedClock());
        s.register(reg("ok@example.com", GOOD, GOOD));
        AuthenticatedUser u = s.login("  OK@example.com ", GOOD);
        assertEquals("ok@example.com", u.email());
        assertFalse(u.toString().contains("pbkdf2"));
    }

    @Test
    void emptyPasswordIsAFailedLoginNotACrash() {
        AuthService s = service(Fakes.fixedClock());
        s.register(reg("empty@example.com", GOOD, GOOD));
        assertThrows(ApiException.Unauthenticated.class, () -> s.login("empty@example.com", ""));
        assertThrows(ApiException.Unauthenticated.class, () -> s.login("empty@example.com", null));
    }

    @Test
    void hasherVerifiesItsOwnHashesAndRejectsTampering() {
        String h = hasher.hash(GOOD.toCharArray());
        assertTrue(hasher.verify(GOOD.toCharArray(), h));
        assertFalse(hasher.verify("correct horse battery stapler".toCharArray(), h));
        assertFalse(hasher.verify(GOOD.toCharArray(), "not-a-hash"));
        assertFalse(hasher.verify(GOOD.toCharArray(), null));
        assertNotEquals(h, hasher.hash(GOOD.toCharArray()));   // random salt each time
    }

    @Test
    void demoHashInV6ScriptMatchesTheDemoPassword() {
        String v6 = "pbkdf2_sha256$600000$Q2l2aWNEZW1vU2FsdC0yNg==$mz647rWd1VdISkJPsSkIHEt63y2egGCQOebGOuOkkM8=";
        assertTrue(new PasswordHasher().verify("CivicConnect-Demo-2026".toCharArray(), v6));
    }
}
