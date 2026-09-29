package com.civicconnect.data.model;

/**
 * Status codes stored in civic.request_status. The permitted TRANSITIONS between them
 * are owned by the request-lifecycle (State pattern) module, not by this persistence layer.
 */
public enum RequestStatusCode {
    SUBMITTED, ASSIGNED, IN_PROGRESS, REOPENED, RESOLVED, CLOSED, REJECTED;

    public static RequestStatusCode fromCode(String code) {
        return valueOf(code.trim().toUpperCase());
    }
}
