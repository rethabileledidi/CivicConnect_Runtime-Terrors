package com.civicconnect.data;

/**
 * Thrown when an optimistic-concurrency check fails: the request was changed by someone
 * else after the caller read it (its version or status no longer matches). The caller
 * should reload the request and let the user retry. (ADR-P3-02)
 */
public class StaleUpdateException extends DataAccessException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public StaleUpdateException(String message) { super(message); }
}
