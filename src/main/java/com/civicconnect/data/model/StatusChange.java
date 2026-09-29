package com.civicconnect.data.model;

import java.util.Objects;

/**
 * A status change that the lifecycle (State pattern) module has ALREADY validated.
 * The repository persists it atomically with its history row and checks that the
 * request is still in {@code fromStatus} at {@code expectedVersion} (optimistic concurrency).
 *
 * @param assigneeId      new assignee, or null to keep the current one
 * @param resolutionNotes required when moving to RESOLVED
 */
public record StatusChange(long requestId, int expectedVersion,
                           RequestStatusCode fromStatus, RequestStatusCode toStatus,
                           long changedBy, Long assigneeId, String note, String resolutionNotes) {
    public StatusChange {
        Objects.requireNonNull(fromStatus, "fromStatus");
        Objects.requireNonNull(toStatus, "toStatus");
        if (fromStatus == toStatus) {
            throw new IllegalArgumentException("A status change must change the status");
        }
    }
}
