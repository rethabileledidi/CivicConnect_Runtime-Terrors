package com.civicconnect.data;

/**
 * A database integrity rule rejected the write (CHECK, FOREIGN KEY, NOT NULL, or the
 * status/history consistency trigger). This is the database acting as the final
 * backstop behind the service-layer rules (defence in depth, A2 Task 2 section 1.3).
 */
public class DataIntegrityException extends DataAccessException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    private final String sqlState;

    public DataIntegrityException(String message, String sqlState, Throwable cause) {
        super(message, cause);
        this.sqlState = sqlState;
    }

    public String getSqlState() { return sqlState; }
}
