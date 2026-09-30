package com.civicconnect.lifecycle;

import com.civicconnect.auth.AuthenticatedUser;

/**
 * Everything a state needs to decide whether a move is allowed.
 *
 * @param actor              the signed-in user asking for the change
 * @param requesterId        who logged the request
 * @param currentAssigneeId  current assignee, or null
 * @param newAssigneeId      assignee chosen for this move (only for moves to ASSIGNED), or null
 * @param note               free-text note for the audit trail
 * @param resolutionNotes    what was done (only for moves to RESOLVED)
 */
public record TransitionContext(AuthenticatedUser actor, long requesterId, Long currentAssigneeId,
                                Long newAssigneeId, String note, String resolutionNotes) {

    public boolean actorIsRequester() { return actor.userId() == requesterId; }

    public boolean actorIsAssignee() { return currentAssigneeId != null && actor.userId() == currentAssigneeId; }

    public boolean actorIsOversight() { return actor.role().isOversight(); }

    public static boolean isBlank(String s) { return s == null || s.isBlank(); }
}
