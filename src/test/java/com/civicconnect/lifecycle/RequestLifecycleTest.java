package com.civicconnect.lifecycle;

import com.civicconnect.auth.AuthenticatedUser;
import com.civicconnect.auth.UserRole;
import com.civicconnect.common.ApiException;
import com.civicconnect.data.model.RequestStatusCode;
import com.civicconnect.data.model.StatusChange;
import com.civicconnect.support.Fakes;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static com.civicconnect.data.model.RequestStatusCode.*;
import static org.junit.jupiter.api.Assertions.*;

/** FR-08 / NFR-06: every from/to pair is either explicitly legal or refused. */
class RequestLifecycleTest {

    private final RequestLifecycle lifecycle = new RequestLifecycle();

    private static final long REQUESTER = 1, STAFF = 2, OTHER_STAFF = 3, COORD = 4;
    private static final AuthenticatedUser requester = Fakes.user(REQUESTER, UserRole.RESIDENT);
    private static final AuthenticatedUser staff = Fakes.user(STAFF, UserRole.STAFF);
    private static final AuthenticatedUser otherStaff = Fakes.user(OTHER_STAFF, UserRole.STAFF);
    private static final AuthenticatedUser coordinator = Fakes.user(COORD, UserRole.COORDINATOR);

    /** The eight legal moves from RequestStates' Javadoc, with the actor allowed to make each. */
    private static final Set<String> LEGAL = Set.of(
            "SUBMITTED>ASSIGNED", "SUBMITTED>REJECTED", "ASSIGNED>IN_PROGRESS", "IN_PROGRESS>RESOLVED",
            "RESOLVED>CLOSED", "RESOLVED>REOPENED", "REOPENED>ASSIGNED", "REOPENED>RESOLVED");

    private static AuthenticatedUser rightActorFor(RequestStatusCode from, RequestStatusCode to) {
        if (to == ASSIGNED || to == REJECTED) return coordinator;
        if (from == RESOLVED) return requester;
        return staff;
    }

    private static TransitionContext fullContext(AuthenticatedUser actor) {
        return new TransitionContext(actor, REQUESTER, STAFF, STAFF, "a note", "fixed the leak");
    }

    @Test
    void everyPairIsEitherLegalOrRefused() {
        int legal = 0, refused = 0;
        for (RequestStatusCode from : RequestStatusCode.values()) {
            for (RequestStatusCode to : RequestStatusCode.values()) {
                if (from == to) continue;
                String key = from + ">" + to;
                AuthenticatedUser actor = rightActorFor(from, to);
                if (LEGAL.contains(key)) {
                    StatusChange ch = lifecycle.plan(10, 3, from, to, fullContext(actor));
                    assertEquals(to, ch.toStatus(), key);
                    legal++;
                } else {
                    assertThrows(ApiException.TransitionNotAllowed.class,
                            () -> lifecycle.plan(10, 3, from, to, fullContext(coordinator)), key);
                    refused++;
                }
            }
        }
        assertEquals(8, legal);
        assertEquals(42 - 8, refused);
    }

    @Test
    void closedAndRejectedAreTerminal() {
        assertTrue(lifecycle.stateOf(CLOSED).moves().isEmpty());
        assertTrue(lifecycle.stateOf(REJECTED).moves().isEmpty());
    }

    @Test
    void onlyOversightCanAssign() {
        TransitionContext byStaff = new TransitionContext(staff, REQUESTER, null, STAFF, null, null);
        assertThrows(ApiException.TransitionNotAllowed.class, () -> lifecycle.plan(1, 0, SUBMITTED, ASSIGNED, byStaff));
        TransitionContext byCoord = new TransitionContext(coordinator, REQUESTER, null, STAFF, null, null);
        assertEquals(STAFF, lifecycle.plan(1, 0, SUBMITTED, ASSIGNED, byCoord).assigneeId());
    }

    @Test
    void assigningNeedsAnAssignee() {
        TransitionContext noAssignee = new TransitionContext(coordinator, REQUESTER, null, null, null, null);
        ApiException e = assertThrows(ApiException.TransitionNotAllowed.class,
                () -> lifecycle.plan(1, 0, SUBMITTED, ASSIGNED, noAssignee));
        assertTrue(e.getMessage().contains("staff member"));
    }

    @Test
    void rejectingNeedsANote() {
        TransitionContext noNote = new TransitionContext(coordinator, REQUESTER, null, null, "  ", null);
        assertThrows(ApiException.TransitionNotAllowed.class, () -> lifecycle.plan(1, 0, SUBMITTED, REJECTED, noNote));
    }

    @Test
    void onlyTheAssigneeCanStartAndResolve() {
        TransitionContext wrongStaff = new TransitionContext(otherStaff, REQUESTER, STAFF, null, null, "done");
        assertThrows(ApiException.TransitionNotAllowed.class, () -> lifecycle.plan(1, 0, ASSIGNED, IN_PROGRESS, wrongStaff));
        assertThrows(ApiException.TransitionNotAllowed.class, () -> lifecycle.plan(1, 0, IN_PROGRESS, RESOLVED, wrongStaff));
        TransitionContext coord = new TransitionContext(coordinator, REQUESTER, STAFF, null, null, "done");
        assertThrows(ApiException.TransitionNotAllowed.class, () -> lifecycle.plan(1, 0, ASSIGNED, IN_PROGRESS, coord));
    }

    @Test
    void resolvingNeedsResolutionNotes() {
        TransitionContext noNotes = new TransitionContext(staff, REQUESTER, STAFF, null, null, "");
        ApiException e = assertThrows(ApiException.TransitionNotAllowed.class,
                () -> lifecycle.plan(1, 0, IN_PROGRESS, RESOLVED, noNotes));
        assertTrue(e.getMessage().contains("what was done"));
        StatusChange ok = lifecycle.plan(1, 0, IN_PROGRESS, RESOLVED,
                new TransitionContext(staff, REQUESTER, STAFF, null, null, "  Replaced valve  "));
        assertEquals("Replaced valve", ok.resolutionNotes());
    }

    @Test
    void onlyTheRequesterCanReopen_andRequesterOrCoordinatorCanClose() {
        assertThrows(ApiException.TransitionNotAllowed.class, () -> lifecycle.plan(1, 0, RESOLVED, REOPENED,
                new TransitionContext(coordinator, REQUESTER, STAFF, null, "came back", null)));
        assertEquals(REOPENED, lifecycle.plan(1, 0, RESOLVED, REOPENED,
                new TransitionContext(requester, REQUESTER, STAFF, null, "came back", null)).toStatus());
        assertEquals(CLOSED, lifecycle.plan(1, 0, RESOLVED, CLOSED,
                new TransitionContext(coordinator, REQUESTER, STAFF, null, null, null)).toStatus());
        assertThrows(ApiException.TransitionNotAllowed.class, () -> lifecycle.plan(1, 0, RESOLVED, CLOSED,
                new TransitionContext(staff, REQUESTER, STAFF, null, null, null)));
    }

    @Test
    void availableMovesDependOnWhoIsAsking() {
        TransitionContext asRequester = new TransitionContext(requester, REQUESTER, STAFF, null, null, null);
        assertEquals(0, lifecycle.availableMoves(SUBMITTED, asRequester).size());
        assertEquals(2, lifecycle.availableMoves(RESOLVED, asRequester).size());   // close or reopen
        TransitionContext asCoord = new TransitionContext(coordinator, REQUESTER, null, null, null, null);
        assertEquals(2, lifecycle.availableMoves(SUBMITTED, asCoord).size());      // assign or reject
        TransitionContext asStaff = new TransitionContext(staff, REQUESTER, STAFF, null, null, null);
        assertEquals(IN_PROGRESS, lifecycle.availableMoves(ASSIGNED, asStaff).get(0).target());
    }

    @Test
    void defaultNotesAreWrittenToHistoryWhenNoneGiven() {
        StatusChange ch = lifecycle.plan(1, 0, ASSIGNED, IN_PROGRESS,
                new TransitionContext(staff, REQUESTER, STAFF, null, null, null));
        assertEquals("Work started", ch.note());
        assertNull(ch.assigneeId());   // only moves to ASSIGNED change the assignee
    }
}
