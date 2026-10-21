package com.civicconnect.service;

import com.civicconnect.auth.AuthenticatedUser;
import com.civicconnect.auth.UserRole;
import com.civicconnect.common.ApiException;
import com.civicconnect.lifecycle.RequestLifecycle;
import com.civicconnect.notification.StatusChangePublisher;
import com.civicconnect.support.Fakes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TC-24 (FR-09 staff comments) and TC-25 (FR-12 restricted categories), closed in M4 under CR-11.
 * Techniques: decision table (who may comment / see), boundary values (comment length 0/1/1000/1001).
 */
class CommentsAndRestrictedAccessTest {

    private final Fakes.Requests requests = new Fakes.Requests();
    private final Fakes.Queries queries = new Fakes.Queries(requests);
    private final Fakes.Users users = new Fakes.Users();
    private final Fakes.Outbox outbox = new Fakes.Outbox();

    private final AuthenticatedUser resident = Fakes.user(1, UserRole.RESIDENT);
    private final AuthenticatedUser staff = Fakes.user(2, UserRole.STAFF);
    private final AuthenticatedUser otherStaff = Fakes.user(3, UserRole.STAFF);
    private final AuthenticatedUser coordinator = Fakes.user(4, UserRole.COORDINATOR);
    private final AuthenticatedUser manager = Fakes.user(5, UserRole.MANAGER);

    private static final int RESTRICTED = Fakes.Queries.RESTRICTED_CATEGORY;

    CommentsAndRestrictedAccessTest() {
        users.add(2, "staff@example.test", UserRole.STAFF, null);
        users.add(3, "staff2@example.test", UserRole.STAFF, null);
    }

    private RequestService service() {
        return new RequestService(requests, queries, users, outbox, new RequestLifecycle(),
                new StatusChangePublisher(List.of()), Fakes.fixedClock());
    }

    // ------------------------------------------------------------------ TC-24 FR-09 comments

    @Test
    void assignedStaffCommentIsStoredAttributedAndShownInDetail() {
        long id = requests.seed("IN_PROGRESS", 1, 2L);
        RequestViews.Detail d = service().addComment(staff, id, "  Waiting for a replacement valve.  ");
        assertEquals(1, d.comments().size());
        assertEquals("Waiting for a replacement valve.", d.comments().get(0).body());   // trimmed
        assertEquals("User 2", d.comments().get(0).author());
        assertNotNull(d.comments().get(0).createdAt());
        assertTrue(d.canComment());
    }

    @Test
    void oversightRolesMayCommentOnRequestsTheyCanSee() {
        long id = requests.seed("SUBMITTED", 1, null);
        assertEquals(1, service().addComment(coordinator, id, "Duplicate of CC-000010?").comments().size());
        assertEquals(2, service().addComment(manager, id, "Checked: not a duplicate.").comments().size());
    }

    @Test
    void residentsCannotCommentAndNeverSeeInternalComments() {
        long id = requests.seed("IN_PROGRESS", 1, 2L);
        service().addComment(staff, id, "Internal note");
        assertThrows(ApiException.Forbidden.class, () -> service().addComment(resident, id, "Any update?"));
        RequestViews.Detail d = service().detail(resident, id);
        assertTrue(d.comments().isEmpty());
        assertFalse(d.canComment());
    }

    @Test
    void staffWhoAreNotTheAssigneeCannotComment_andGetNotFound() {
        long id = requests.seed("IN_PROGRESS", 1, 2L);
        assertThrows(ApiException.NotFound.class, () -> service().addComment(otherStaff, id, "Can I help?"));
        assertTrue(queries.findComments(id).isEmpty());
    }

    @Test
    void commentLengthBoundaries() {
        long id = requests.seed("IN_PROGRESS", 1, 2L);
        for (String bad : new String[]{null, "", "   ", "x".repeat(1001)}) {
            ApiException.Validation e = assertThrows(ApiException.Validation.class,
                    () -> service().addComment(staff, id, bad));
            assertTrue(e.fieldErrors().containsKey("body"));
        }
        assertTrue(queries.findComments(id).isEmpty(), "nothing stored for invalid comments");
        service().addComment(staff, id, "x");                     // 1: lower edge accepted
        service().addComment(staff, id, "y".repeat(1000));        // 1000: upper edge accepted
        assertEquals(2, queries.findComments(id).size());
    }

    // ------------------------------------------------------------------ TC-25 FR-12 restricted categories

    @Test
    void coordinatorIsDeniedARestrictedRequest_andGetsNotFoundNotForbidden() {
        long id = requests.seed("SUBMITTED", 1, null, RESTRICTED);
        assertThrows(ApiException.NotFound.class, () -> service().detail(coordinator, id));
        assertThrows(ApiException.NotFound.class, () -> service().addComment(coordinator, id, "note"));
    }

    @Test
    void authorisedRolesStillSeeRestrictedRequests() {
        long submitted = requests.seed("SUBMITTED", 1, null, RESTRICTED);
        long assigned = requests.seed("IN_PROGRESS", 1, 2L, RESTRICTED);
        assertEquals(submitted, service().detail(resident, submitted).request().id());   // own request
        assertEquals(submitted, service().detail(manager, submitted).request().id());
        assertEquals(assigned, service().detail(staff, assigned).request().id());        // the assignee
        assertThrows(ApiException.NotFound.class, () -> service().detail(otherStaff, assigned));
    }

    @Test
    void coordinatorQueueLeavesOutRestrictedRequests_managerQueueKeepsThem() {
        long normal = requests.seed("SUBMITTED", 1, null);
        long restricted = requests.seed("SUBMITTED", 1, null, RESTRICTED);
        List<Long> coordQueue = service().queue(coordinator, null).stream().map(RequestViews.Summary::id).toList();
        List<Long> mgrQueue = service().queue(manager, null).stream().map(RequestViews.Summary::id).toList();
        assertEquals(List.of(normal), coordQueue);
        assertEquals(List.of(normal, restricted), mgrQueue);
    }

    @Test
    void managerAssignsARestrictedRequestAndOnlyThatStaffMemberGetsIt() {
        long id = requests.seed("SUBMITTED", 1, null, RESTRICTED);
        int version = requests.rows.get(id).version();
        service().changeStatus(manager, id, new RequestService.ChangeInput("ASSIGNED", version, 2L, null, null));
        assertEquals(List.of(id), service().queue(staff, null).stream().map(RequestViews.Summary::id).toList());
        assertTrue(service().queue(otherStaff, null).isEmpty());
        assertTrue(service().queue(coordinator, null).isEmpty());
    }

    @Test
    void coordinatorsStillSeeUnrestrictedRequests() {
        long id = requests.seed("SUBMITTED", 1, null);
        assertEquals(id, service().detail(coordinator, id).request().id());
    }
}
