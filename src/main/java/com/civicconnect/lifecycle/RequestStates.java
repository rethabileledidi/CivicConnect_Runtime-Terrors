package com.civicconnect.lifecycle;

import com.civicconnect.data.model.RequestStatusCode;

import java.util.List;

import static com.civicconnect.data.model.RequestStatusCode.ASSIGNED;
import static com.civicconnect.data.model.RequestStatusCode.CLOSED;
import static com.civicconnect.data.model.RequestStatusCode.IN_PROGRESS;
import static com.civicconnect.data.model.RequestStatusCode.REJECTED;
import static com.civicconnect.data.model.RequestStatusCode.REOPENED;
import static com.civicconnect.data.model.RequestStatusCode.RESOLVED;
import static com.civicconnect.data.model.RequestStatusCode.SUBMITTED;

/**
 * The seven concrete states. Legal moves (8 of the 42 possible from/to pairs):
 * <pre>
 *   SUBMITTED   -> ASSIGNED    coordinator/manager/admin, must choose a staff assignee
 *   SUBMITTED   -> REJECTED    coordinator/manager/admin, note required
 *   ASSIGNED    -> IN_PROGRESS the assigned staff member only
 *   IN_PROGRESS -> RESOLVED    the assigned staff member only, resolution notes required
 *   RESOLVED    -> CLOSED      the requester (confirms the fix) or coordinator/manager/admin
 *   RESOLVED    -> REOPENED    the requester only, note required (problem came back)
 *   REOPENED    -> ASSIGNED    coordinator/manager/admin, must choose a staff assignee
 *   REOPENED    -> RESOLVED    the assigned staff member, resolution notes required
 *   CLOSED, REJECTED           terminal: no moves
 * </pre>
 * These are the same paths Rethabile's V4 sample data walks, so the demo data is consistent.
 */
public final class RequestStates {

    private RequestStates() { }

    private static final String OVERSIGHT_ONLY = "Only a coordinator or manager can do this.";
    private static final String ASSIGNEE_ONLY = "Only the staff member assigned to this request can do this.";

    public static final class Submitted extends RequestState {
        @Override public RequestStatusCode code() { return SUBMITTED; }
        @Override public List<Move> moves() {
            return List.of(new Move(ASSIGNED, "Assign", true, false, false),
                           new Move(REJECTED, "Reject", false, true, false));
        }
        @Override protected String refusal(RequestStatusCode target, TransitionContext ctx) {
            return ctx.actorIsOversight() ? null : OVERSIGHT_ONLY;
        }
    }

    public static final class Assigned extends RequestState {
        @Override public RequestStatusCode code() { return ASSIGNED; }
        @Override public List<Move> moves() {
            return List.of(new Move(IN_PROGRESS, "Start work", false, false, false));
        }
        @Override protected String refusal(RequestStatusCode target, TransitionContext ctx) {
            return ctx.actorIsAssignee() ? null : ASSIGNEE_ONLY;
        }
    }

    public static final class InProgress extends RequestState {
        @Override public RequestStatusCode code() { return IN_PROGRESS; }
        @Override public List<Move> moves() {
            return List.of(new Move(RESOLVED, "Resolve", false, false, true));
        }
        @Override protected String refusal(RequestStatusCode target, TransitionContext ctx) {
            return ctx.actorIsAssignee() ? null : ASSIGNEE_ONLY;
        }
    }

    public static final class Resolved extends RequestState {
        @Override public RequestStatusCode code() { return RESOLVED; }
        @Override public List<Move> moves() {
            return List.of(new Move(CLOSED, "Confirm and close", false, false, false),
                           new Move(REOPENED, "Reopen", false, true, false));
        }
        @Override protected String refusal(RequestStatusCode target, TransitionContext ctx) {
            if (target == CLOSED) {
                return ctx.actorIsRequester() || ctx.actorIsOversight() ? null
                        : "Only the requester or a coordinator can close a request.";
            }
            return ctx.actorIsRequester() ? null : "Only the person who logged the request can reopen it.";
        }
    }

    public static final class Reopened extends RequestState {
        @Override public RequestStatusCode code() { return REOPENED; }
        @Override public List<Move> moves() {
            return List.of(new Move(ASSIGNED, "Re-assign", true, false, false),
                           new Move(RESOLVED, "Resolve", false, false, true));
        }
        @Override protected String refusal(RequestStatusCode target, TransitionContext ctx) {
            if (target == ASSIGNED) return ctx.actorIsOversight() ? null : OVERSIGHT_ONLY;
            return ctx.actorIsAssignee() ? null : ASSIGNEE_ONLY;
        }
    }

    public static final class Closed extends RequestState {
        @Override public RequestStatusCode code() { return CLOSED; }
        @Override public List<Move> moves() { return List.of(); }
        @Override protected String refusal(RequestStatusCode target, TransitionContext ctx) { return "Closed requests are final."; }
    }

    public static final class Rejected extends RequestState {
        @Override public RequestStatusCode code() { return REJECTED; }
        @Override public List<Move> moves() { return List.of(); }
        @Override protected String refusal(RequestStatusCode target, TransitionContext ctx) { return "Rejected requests are final."; }
    }
}
