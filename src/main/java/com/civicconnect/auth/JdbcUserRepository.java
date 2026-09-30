package com.civicconnect.auth;

import com.civicconnect.data.DataIntegrityException;
import com.civicconnect.data.TransactionRunner;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link UserRepository}. All statements are parameterised. */
public final class JdbcUserRepository implements UserRepository {

    private static final String COLUMNS = """
            user_id, email, full_name, phone, municipality, role_code, department_id,
            password_hash, is_active, failed_login_count, locked_until""";

    private final TransactionRunner tx;

    public JdbcUserRepository(TransactionRunner tx) {
        this.tx = tx;
    }

    @Override
    public Optional<UserAccount> findByEmail(String email) {
        return tx.readOnlySnapshot(c -> one(c, "SELECT " + COLUMNS + " FROM civic.app_user WHERE lower(email) = lower(?)", email));
    }

    @Override
    public Optional<UserAccount> findById(long userId) {
        return tx.readOnlySnapshot(c -> one(c, "SELECT " + COLUMNS + " FROM civic.app_user WHERE user_id = ?", userId));
    }

    @Override
    public UserAccount createResident(String email, String fullName, String phone, String municipality, String passwordHash) {
        String sql = "INSERT INTO civic.app_user (email, full_name, phone, municipality, role_code, password_hash) "
                + "VALUES (?, ?, ?, ?, 'RESIDENT', ?) RETURNING " + COLUMNS;
        try {
            return tx.inTransaction(c -> {
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setString(1, email);
                    ps.setString(2, fullName);
                    ps.setString(3, phone);
                    ps.setString(4, municipality);
                    ps.setString(5, passwordHash);
                    try (ResultSet rs = ps.executeQuery()) {
                        rs.next();
                        return map(rs);
                    }
                }
            });
        } catch (DataIntegrityException e) {
            if ("23505".equals(e.getSqlState())) throw new DuplicateEmail();   // unique_violation on ux_app_user_email
            throw e;
        }
    }

    @Override
    public void recordFailedLogin(long userId, int failedLoginCount, Instant lockedUntil) {
        tx.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE civic.app_user SET failed_login_count = ?, locked_until = ? WHERE user_id = ?")) {
                ps.setInt(1, failedLoginCount);
                if (lockedUntil == null) ps.setNull(2, Types.TIMESTAMP_WITH_TIMEZONE);
                else ps.setTimestamp(2, Timestamp.from(lockedUntil));
                ps.setLong(3, userId);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public void recordSuccessfulLogin(long userId, Instant at) {
        tx.inTransaction(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE civic.app_user SET failed_login_count = 0, locked_until = NULL, last_login_at = ? WHERE user_id = ?")) {
                ps.setTimestamp(1, Timestamp.from(at));
                ps.setLong(2, userId);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public List<UserAccount> findActiveStaff() {
        return tx.readOnlySnapshot(c -> {
            List<UserAccount> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("SELECT " + COLUMNS
                    + " FROM civic.app_user WHERE role_code = 'STAFF' AND is_active ORDER BY full_name")) {
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) list.add(map(rs));
                }
            }
            return list;
        });
    }

    private static Optional<UserAccount> one(Connection c, String sql, Object key) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(map(rs)) : Optional.empty();
            }
        }
    }

    private static UserAccount map(ResultSet rs) throws SQLException {
        int dept = rs.getInt("department_id");
        Integer departmentId = rs.wasNull() ? null : dept;
        Timestamp locked = rs.getTimestamp("locked_until");
        return new UserAccount(
                rs.getLong("user_id"),
                rs.getString("email"),
                rs.getString("full_name"),
                rs.getString("phone"),
                rs.getString("municipality"),
                UserRole.fromCode(rs.getString("role_code")),
                departmentId,
                rs.getString("password_hash"),
                rs.getBoolean("is_active"),
                rs.getInt("failed_login_count"),
                locked == null ? null : locked.toInstant());
    }
}
