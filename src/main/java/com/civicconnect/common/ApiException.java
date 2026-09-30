package com.civicconnect.common;

import java.util.Map;

/**
 * Base class for business-rule failures that the JSON API turns into an HTTP status.
 * Services throw these; they never throw servlet or SQL types (layering rule, PED §7).
 */
public abstract class ApiException extends RuntimeException {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    protected ApiException(String message) { super(message); }

    /** HTTP status the API layer should return. */
    public abstract int httpStatus();

    /** Short machine-readable code for the frontend (e.g. "VALIDATION_FAILED"). */
    public abstract String code();

    /** Field-level messages, empty unless this is a validation failure. */
    public Map<String, String> fieldErrors() { return Map.of(); }

    // ------------------------------------------------------------------ concrete types

    /** 400: the input broke a validation rule. */
    public static final class Validation extends ApiException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
        private final Map<String, String> fieldErrors;

        public Validation(Map<String, String> fieldErrors) {
            super("Please fix the highlighted fields.");
            this.fieldErrors = Map.copyOf(fieldErrors);
        }

        public Validation(String field, String message) { this(Map.of(field, message)); }

        @Override public int httpStatus() { return 400; }
        @Override public String code() { return "VALIDATION_FAILED"; }
        @Override public Map<String, String> fieldErrors() { return fieldErrors; }
    }

    /** 401: not signed in, or sign-in failed. */
    public static final class Unauthenticated extends ApiException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
        public Unauthenticated(String message) { super(message); }
        @Override public int httpStatus() { return 401; }
        @Override public String code() { return "UNAUTHENTICATED"; }
    }

    /** 403: signed in, but this role may not do this. */
    public static final class Forbidden extends ApiException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
        public Forbidden(String message) { super(message); }
        @Override public int httpStatus() { return 403; }
        @Override public String code() { return "FORBIDDEN"; }
    }

    /**
     * 404: the record does not exist OR the caller may not know it exists. Returning 404
     * (not 403) for another resident's request stops reference-number guessing (NFR-02).
     */
    public static final class NotFound extends ApiException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
        public NotFound(String message) { super(message); }
        @Override public int httpStatus() { return 404; }
        @Override public String code() { return "NOT_FOUND"; }
    }

    /** 409: a conflict, e.g. duplicate email or a stale (concurrently changed) request. */
    public static final class Conflict extends ApiException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
        private final String code;
        public Conflict(String code, String message) { super(message); this.code = code; }
        @Override public int httpStatus() { return 409; }
        @Override public String code() { return code; }
    }

    /** 422: the lifecycle rules do not allow this status change (FR-08). */
    public static final class TransitionNotAllowed extends ApiException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
        public TransitionNotAllowed(String message) { super(message); }
        @Override public int httpStatus() { return 422; }
        @Override public String code() { return "TRANSITION_NOT_ALLOWED"; }
    }

    /** 423: the account is temporarily locked after repeated failed sign-ins. */
    public static final class Locked extends ApiException {
        @java.io.Serial
        private static final long serialVersionUID = 1L;
        public Locked(String message) { super(message); }
        @Override public int httpStatus() { return 423; }
        @Override public String code() { return "ACCOUNT_LOCKED"; }
    }
}
