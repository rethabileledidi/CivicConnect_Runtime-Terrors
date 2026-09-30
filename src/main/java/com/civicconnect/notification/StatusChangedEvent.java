package com.civicconnect.notification;

import com.civicconnect.data.model.RequestStatusCode;

import java.time.Instant;

/**
 * Published AFTER a status change (or a new request) has been committed together with its
 * history row. Listeners react to it; none of them can undo it.
 *
 * @param fromStatus    null when the request has just been created
 * @param contactPhone  number captured on the request form (may be null)
 */
public record StatusChangedEvent(long requestId, String referenceNo, String title,
                                 long requesterId, RequestStatusCode fromStatus, RequestStatusCode toStatus,
                                 long changedBy, Instant changedAt, String note, String contactPhone) {

    public boolean isNewRequest() { return fromStatus == null; }

    /** The requester made this change themselves (e.g. closing or reopening). */
    public boolean changedByRequester() { return changedBy == requesterId; }
}
