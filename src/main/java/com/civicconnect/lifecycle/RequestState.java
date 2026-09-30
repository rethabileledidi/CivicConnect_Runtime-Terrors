package com.civicconnect.lifecycle;

import com.civicconnect.data.model.RequestStatusCode;

import java.util.List;

/**
 * STATE PATTERN (PED DP-1, ADR-04; A2 Task 1 problem P1).
 * <p>
 * Each status is one small class that knows (a) which statuses it may move to and
 * (b) who may make each move and what the move needs (a note, an assignee, resolution notes).
 * A plain transition table cannot express those obligations, which is why A2 preferred
 * State over a table; Strategy was rejected because it chooses algorithms, not lifecycle stages.
 * <p>
 * The database repeats the data obligations as CHECK constraints (V1) as a backstop.
 */
public abstract sealed class RequestState
        permits RequestStates.Submitted, RequestStates.Assigned, RequestStates.InProgress,
                RequestStates.Resolved, RequestStates.Reopened, RequestStates.Closed, RequestStates.Rejected {

    /** The status this state represents. */
    public abstract RequestStatusCode code();

    /** Every move this state allows, with its obligations (used to build the UI's buttons). */
    public abstract List<Move> moves();

    /**
     * Returns null if {@code ctx.actor()} may move to {@code target}, otherwise the reason why not.
     * Only called for targets that appear in {@link #moves()}.
     */
    protected abstract String refusal(RequestStatusCode target, TransitionContext ctx);

    /** Throws {@link com.civicconnect.common.ApiException.TransitionNotAllowed} unless the move is legal. */
    public final void check(RequestStatusCode target, TransitionContext ctx) {
        Move move = moves().stream().filter(m -> m.target() == target).findFirst().orElse(null);
        if (move == null) {
            throw new com.civicconnect.common.ApiException.TransitionNotAllowed(
                    "A request that is " + display(code()) + " cannot move to " + display(target) + ".");
        }
        String reason = refusal(target, ctx);
        if (reason == null) reason = missingInput(move, ctx);
        if (reason != null) throw new com.civicconnect.common.ApiException.TransitionNotAllowed(reason);
    }

    /** True if the actor could make this move (ignores missing notes, which the form collects). */
    public final boolean permits(RequestStatusCode target, TransitionContext ctx) {
        return moves().stream().anyMatch(m -> m.target() == target) && refusal(target, ctx) == null;
    }

    private static String missingInput(Move move, TransitionContext ctx) {
        if (move.needsAssignee() && ctx.newAssigneeId() == null) return "Choose a staff member to assign.";
        if (move.needsNote() && TransitionContext.isBlank(ctx.note())) return "A note is required for this action.";
        if (move.needsResolutionNotes() && TransitionContext.isBlank(ctx.resolutionNotes())) {
            return "Describe what was done before resolving the request.";
        }
        return null;
    }

    public static String display(RequestStatusCode code) {
        return switch (code) {
            case SUBMITTED -> "Submitted";
            case ASSIGNED -> "Assigned";
            case IN_PROGRESS -> "In Progress";
            case REOPENED -> "Reopened";
            case RESOLVED -> "Resolved";
            case CLOSED -> "Closed";
            case REJECTED -> "Rejected";
        };
    }

    /**
     * One legal move out of a state.
     *
     * @param label a button label for the UI
     */
    public record Move(RequestStatusCode target, String label, boolean needsAssignee,
                       boolean needsNote, boolean needsResolutionNotes) { }
}
