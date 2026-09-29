package com.civicconnect.data;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Owns transaction boundaries so repositories never commit on their own.
 *
 * <ul>
 *   <li>{@link #inTransaction} - READ COMMITTED read/write unit of work (status change + history).</li>
 *   <li>{@link #readOnlySnapshot} - READ ONLY, REPEATABLE READ: every query inside sees the same
 *       database snapshot and the same now(), so all figures on one dashboard/report agree.</li>
 * </ul>
 */
public final class TransactionRunner {

    @FunctionalInterface
    public interface SqlWork<T> {
        T execute(Connection connection) throws SQLException;
    }

    private final DataSource dataSource;

    public TransactionRunner(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public <T> T inTransaction(SqlWork<T> work) {
        return run(work, Connection.TRANSACTION_READ_COMMITTED, false);
    }

    public <T> T readOnlySnapshot(SqlWork<T> work) {
        return run(work, Connection.TRANSACTION_REPEATABLE_READ, true);
    }

    private <T> T run(SqlWork<T> work, int isolation, boolean readOnly) {
        try (Connection c = dataSource.getConnection()) {
            boolean originalAutoCommit = c.getAutoCommit();
            int originalIsolation = c.getTransactionIsolation();
            try {
                c.setAutoCommit(false);
                c.setTransactionIsolation(isolation);
                c.setReadOnly(readOnly);
                T result = work.execute(c);
                c.commit();
                return result;
            } catch (SQLException | RuntimeException e) {
                safeRollback(c, e);
                throw translate(e);
            } finally {
                // Return pooled connections in their original state
                try {
                    c.setReadOnly(false);
                    c.setTransactionIsolation(originalIsolation);
                    c.setAutoCommit(originalAutoCommit);
                } catch (SQLException ignored) {
                    // connection is being closed anyway
                }
            }
        } catch (SQLException e) {
            throw translate(e);
        }
    }

    private static void safeRollback(Connection c, Exception cause) {
        try {
            c.rollback();
        } catch (SQLException rollbackFailure) {
            cause.addSuppressed(rollbackFailure);
        }
    }

    /** Maps PostgreSQL SQLSTATE codes to meaningful application exceptions. */
    static RuntimeException translate(Exception e) {
        if (e instanceof DataAccessException dae) return dae;
        if (e instanceof RuntimeException re) return re;
        SQLException sql = (SQLException) e;
        String state = sql.getSQLState();
        if (state != null && state.startsWith("23")) {
            // 23xxx = integrity constraint violation (check, FK, not null, unique, custom trigger)
            return new DataIntegrityException(sql.getMessage(), state, sql);
        }
        if ("40001".equals(state)) {
            return new StaleUpdateException("Concurrent update detected; reload and try again");
        }
        return new DataAccessException("Database operation failed: " + sql.getMessage(), sql);
    }
}
