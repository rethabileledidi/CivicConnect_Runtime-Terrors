package com.civicconnect.lifecycle;

import com.civicconnect.data.model.RequestStatusCode;
import com.civicconnect.data.model.StatusChange;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Entry point to the State pattern. The service layer asks it two questions:
 * "which actions can this user take?" (to build buttons) and
 * "turn this request into a validated StatusChange, or refuse" (before persisting).
 * It never touches the database: the result is handed to Rethabile's
 * {@link com.civicconnect.data.ServiceRequestRepository#changeStatus}.
 */
public final class RequestLifecycle {

    private final Map<RequestStatusCode, RequestState> states = new EnumMap<>(RequestStatusCode.class);

    public RequestLifecycle() {
        for (RequestState s : List.of(new RequestStates.Submitted(), new RequestStates.Assigned(),
                new RequestStates.InProgress(), new RequestStates.Resolved(), new RequestStates.Reopened(),
                new RequestStates.Closed(), new RequestStates.Rejected())) {
            states.put(s.code(), s);
        }
        if (states.size() != RequestStatusCode.values().length) {
            throw new IllegalStateException("Every status needs a state class");
        }
    }

    public RequestState stateOf(RequestStatusCode code) {
        return states.get(code);
    }

    /** The moves the actor may take now (the UI shows exactly these buttons). */
    public List<RequestState.Move> availableMoves(RequestStatusCode current, TransitionContext ctx) {
        RequestState state = stateOf(current);
        return state.moves().stream().filter(m -> state.permits(m.target(), ctx)).toList();
    }

    /**
     * Validates the move and returns the StatusChange to persist.
     *
     * @throws com.civicconnect.common.ApiException.TransitionNotAllowed if the move is illegal
     */
    public StatusChange plan(long requestId, int expectedVersion, RequestStatusCode current,
                             RequestStatusCode target, TransitionContext ctx) {
        stateOf(current).check(target, ctx);
        Long assignee = target == RequestStatusCode.ASSIGNED ? ctx.newAssigneeId() : null;
        String resolution = target == RequestStatusCode.RESOLVED ? ctx.resolutionNotes().strip() : null;
        String note = TransitionContext.isBlank(ctx.note()) ? defaultNote(target) : ctx.note().strip();
        return new StatusChange(requestId, expectedVersion, current, target,
                ctx.actor().userId(), assignee, note, resolution);
    }

    static String defaultNote(RequestStatusCode target) {
        return switch (target) {
            case ASSIGNED -> "Assigned to field team";
            case IN_PROGRESS -> "Work started";
            case RESOLVED -> "Work completed";
            case CLOSED -> "Closed";
            default -> RequestState.display(target);
        };
    }
}
