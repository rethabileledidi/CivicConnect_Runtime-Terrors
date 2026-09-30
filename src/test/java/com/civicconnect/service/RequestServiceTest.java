package com.civicconnect.service;

import com.civicconnect.auth.AuthenticatedUser;
import com.civicconnect.auth.UserRole;
import com.civicconnect.common.ApiException;
import com.civicconnect.lifecycle.RequestLifecycle;
import com.civicconnect.notification.Listeners;
import com.civicconnect.notification.OutboxRepository;
import com.civicconnect.notification.StatusChangeListener;
import com.civicconnect.notification.StatusChangePublisher;
import com.civicconnect.notification.StatusChangedEvent;
import com.civicconnect.support.Fakes;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Business-layer behaviour of RequestService against in-memory fakes (no database). */
class RequestServiceTest {

    private final Fakes.Requests requests = new Fakes.Requests();
    private final Fakes.Queries queries = new Fakes.Queries(requests);
    private final Fakes.Users users = new Fakes.Users();
    private final Fakes.Notifications notifications = new Fakes.Notifications();
    private final Fakes.Outbox outbox = new Fakes.Outbox();
    private final List<StatusChangeListener> listeners = new ArrayList<>(List.of(
            new Listeners.InAppNotificationListener(notifications),
            new Listeners.SimulatedMessagingListener(outbox)));

    private final AuthenticatedUser resident = Fakes.user(1, UserRole.RESIDENT);
    private final AuthenticatedUser otherResident = Fakes.user(9, UserRole.RESIDENT);
    private final AuthenticatedUser staff = Fakes.user(2, UserRole.STAFF);
    private final AuthenticatedUser coordinator = Fakes.user(4, UserRole.COORDINATOR);

    RequestServiceTest() {
        users.add(2, "staff@example.test", UserRole.STAFF, null);
        users.add(4, "coord@example.test", UserRole.COORDINATOR, null);
    }

    private RequestService service() {
        return new RequestService(requests, queries, users, outbox, new RequestLifecycle(),
                new StatusChangePublisher(listeners), Fakes.fixedClock());
    }

    private static RequestService.SubmitInput validInput() {
        return new RequestService.SubmitInput("Burst pipe on Church St", "Clean water has been running down the road since 6am.",
                3, "high", "12 Church Street", "Hatfield", "Pretoria", "0831112222");
    }

    @Test
    void submitCreatesRequestWithHistoryAndNotifies() {
        RequestViews.Detail d = service().submit(resident, validInput());
        assertEquals("SUBMITTED", d.request().status());
        assertEquals("12 Church Street, Hatfield, Pretoria", d.request().location());
        assertEquals(1, d.history().size());
        assertEquals(List.of(1L), notifications.recipients);          // "request received"
        assertEquals(2, outbox.messages.size());                       // one simulated SMS + one WhatsApp
        assertEquals("0831112222", queries.phones.get(d.request().id()));
        assertTrue(d.actions().isEmpty());                             // a resident cannot move a Submitted request
    }

    @Test
    void submitValidatesEveryFieldServerSide() {
        RequestService.SubmitInput bad = new RequestService.SubmitInput("Hi", "short", 999, "whenever", "", "", "", "123");
        ApiException.Validation e = assertThrows(ApiException.Validation.class, () -> service().submit(resident, bad));
        for (String field : List.of("category", "title", "description", "priority", "address", "suburb", "city", "contactNumber")) {
            assertTrue(e.fieldErrors().containsKey(field), field);
        }
        assertTrue(requests.rows.isEmpty());
    }

    @Test
    void residentsCannotSeeOtherResidentsRequests_andGetNotFoundNotForbidden() {
        long id = service().submit(resident, validInput()).request().id();
        assertThrows(ApiException.NotFound.class, () -> service().detail(otherResident, id));
        assertThrows(ApiException.NotFound.class, () -> service().detail(staff, id));     // not assigned yet
        assertEquals(id, service().detail(coordinator, id).request().id());
    }

    @Test
    void fullLifecycleFromSubmitToClose() {
        RequestService s = service();
        long id = s.submit(resident, validInput()).request().id();

        var assigned = s.changeStatus(coordinator, id, new RequestService.ChangeInput("ASSIGNED", 0, 2L, null, null));
        assertEquals("ASSIGNED", assigned.request().request().status());
        assertTrue(assigned.warnings().isEmpty());

        s.changeStatus(staff, id, new RequestService.ChangeInput("IN_PROGRESS", 1, null, null, null));
        s.changeStatus(staff, id, new RequestService.ChangeInput("RESOLVED", 2, null, null, "Valve replaced"));
        RequestViews.Detail forResident = s.detail(resident, id);
        assertEquals(2, forResident.actions().size());                 // confirm-and-close or reopen
        assertTrue(forResident.canGiveFeedback());

        s.changeStatus(resident, id, new RequestService.ChangeInput("CLOSED", 3, null, null, null));
        assertEquals(5, requests.findHistory(id).size());              // one history row per change
        // requester is notified of staff changes, not of their own close
        assertEquals(4, notifications.recipients.size());
    }

    @Test
    void illegalMovesNeverReachTheRepository() {
        long id = service().submit(resident, validInput()).request().id();
        assertThrows(ApiException.TransitionNotAllowed.class,
                () -> service().changeStatus(coordinator, id, new RequestService.ChangeInput("CLOSED", 0, null, null, null)));
        assertEquals(0, requests.changeCalls);
    }

    @Test
    void staleVersionBecomesAConflictThatAsksTheUserToReload() {
        long id = service().submit(resident, validInput()).request().id();
        ApiException.Conflict e = assertThrows(ApiException.Conflict.class, () -> service().changeStatus(coordinator, id,
                new RequestService.ChangeInput("ASSIGNED", 7, 2L, null, null)));
        assertEquals("STALE_REQUEST", e.code());
        assertTrue(e.getMessage().contains("Reload"));
    }

    @Test
    void canOnlyAssignToActiveStaff() {
        long id = service().submit(resident, validInput()).request().id();
        ApiException.Validation e = assertThrows(ApiException.Validation.class, () -> service().changeStatus(coordinator, id,
                new RequestService.ChangeInput("ASSIGNED", 0, 4L, null, null)));   // user 4 is a coordinator
        assertTrue(e.fieldErrors().containsKey("assigneeId"));
    }

    @Test
    void aFailingListenerDoesNotUndoTheStatusChange() {
        listeners.add(new StatusChangeListener() {
            @Override public String name() { return "Broken listener"; }
            @Override public void onStatusChanged(StatusChangedEvent event) { throw new IllegalStateException("boom"); }
        });
        RequestService s = service();
        long id = s.submit(resident, validInput()).request().id();
        var result = s.changeStatus(coordinator, id, new RequestService.ChangeInput("ASSIGNED", 0, 2L, null, null));
        assertEquals("ASSIGNED", requests.rows.get(id).statusCode());
        assertEquals(1, result.warnings().size());
        assertTrue(result.warnings().get(0).contains("status change itself was saved"));
    }

    @Test
    void staffQueueShowsOnlyTheirAssignments_residentsHaveNoQueue() {
        requests.seed("ASSIGNED", 1, 2L);
        requests.seed("ASSIGNED", 1, 5L);
        requests.seed("SUBMITTED", 1, null);
        assertEquals(1, service().queue(staff, null).size());
        assertEquals(3, service().queue(coordinator, null).size());
        assertEquals(1, service().queue(coordinator, "submitted").size());
        assertThrows(ApiException.Forbidden.class, () -> service().queue(resident, null));
    }

    @Test
    void feedbackOnlyByRequesterOnceResolved_andOnlyOnce() {
        long id = requests.seed("RESOLVED", 1, 2L);
        assertThrows(ApiException.NotFound.class, () -> service().giveFeedback(otherResident, id, 5, "great"));
        assertThrows(ApiException.Validation.class, () -> service().giveFeedback(resident, id, 9, null));
        RequestViews.Detail d = service().giveFeedback(resident, id, 4, "Quick fix");
        assertEquals(4, d.feedback().rating());
        assertFalse(d.canGiveFeedback());
        assertThrows(ApiException.Conflict.class, () -> service().giveFeedback(resident, id, 5, null));
        long open = requests.seed("IN_PROGRESS", 1, 2L);
        assertThrows(ApiException.TransitionNotAllowed.class, () -> service().giveFeedback(resident, open, 5, null));
    }

    @Test
    void simulatedMessagesShownToUsersHaveMaskedNumbers() {
        long id = service().submit(resident, validInput()).request().id();
        RequestViews.Detail d = service().detail(resident, id);
        assertEquals(2, d.messages().size());
        assertEquals("******2222", d.messages().get(0).recipient());
        assertEquals(OutboxRepository.Channel.SMS.name(), d.messages().get(0).channel());
    }
}
