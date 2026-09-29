package com.civicconnect.data;

/** Unchecked wrapper for database failures, so callers are not coupled to java.sql. */
public class DataAccessException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public DataAccessException(String message) { super(message); }
    public DataAccessException(String message, Throwable cause) { super(message, cause); }
}
