package com.civicconnect.service;

import com.civicconnect.auth.AuthenticatedUser;
import com.civicconnect.auth.UserAccount;
import com.civicconnect.auth.UserRepository;
import com.civicconnect.auth.UserRole;
import com.civicconnect.common.ApiException;
import com.civicconnect.common.Validator;
import com.civicconnect.data.DataIntegrityException;
import com.civicconnect.data.RecordNotFoundException;
import com.civicconnect.data.ServiceRequestRepository;
import com.civicconnect.data.StaleUpdateException;
import com.civicconnect.data.model.CreatedRequest;
import com.civicconnect.data.model.NewServiceRequest;
import com.civicconnect.data.model.RequestStatusCode;
import com.civicconnect.data.model.ServiceRequestRecord;
import com.civicconnect.data.model.StatusChange;
import com.civicconnect.lifecycle.RequestLifecycle;
import com.civicconnect.lifecycle.TransitionContext;
import com.civicconnect.notification.OutboxRepository;
import com.civicconnect.notification.StatusChangePublisher;
import com.civicconnect.notification.StatusChangedEvent;

import java.time.Clock;
import java.util.List;
import java.util.Set;

/**
 * Request use cases (Business layer). Order of work for a status change - the FR-08 trace:
 * <ol>
 *   <li>load the request and check the caller may see it ({@link AccessPolicy});</li>
 *   <li>ask the State pattern whether the move is legal ({@link RequestLifecycle#plan});</li>
 *   <li>persist status + history atomically with an optimistic version check
 *       (Rethabile's {@link ServiceRequestRepository#changeStatus});</li>
 *   <li>only after the commit, notify observers ({@link StatusChangePublisher}).</li>
 * </ol>
 */
public final class RequestService {

    public static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");
    private static final int LIST_LIMIT = 200;

    private final ServiceRequestRepository requests;
    private final RequestQueries queries;
    private final UserRepository users;
    private final OutboxRepository outbox;
    private final RequestLifecycle lifecycle;
    private final StatusChangePublisher publisher;
    private final Clock clock;

    public RequestService(ServiceRequestRepository requests, RequestQueries queries, UserRepository users,
                          OutboxRepository outbox, RequestLifecycle lifecycle, StatusChangePublisher publisher,
                          Clock clock) {
        this.requests = requests;
        this.queries = queries;
        this.users = users;
        this.outbox = outbox;
        this.lifecycle = lifecycle;
        this.publisher = publisher;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ submit (FR-01, FR-02)

    public record SubmitInput(String title, String description, Integer categoryId, String priority,
                              String address, String suburb, String city, String contactPhone) { }

    public RequestViews.Detail submit(AuthenticatedUser actor, SubmitInput in) {
        requireSignedIn(actor);
        String priority = Validator.clean(in.priority()).toUpperCase();
        String phone = Validator.clean(in.contactPhone());
        String location = String.join(", ", Validator.clean(in.address()), Validator.clean(in.suburb()),
                Validator.clean(in.city()));
        boolean categoryOk = in.categoryId() != null && queries.listActiveCategories().stream()
                .anyMatch(c -> c.categoryId() == in.categoryId());

        new Validator()
                .require(categoryOk, "category", "Choose a category.")
                .length("title", in.title(), 5, 150, "Title")
                .length("description", in.description(), 20, 2000, "Description")
                .require(PRIORITIES.contains(priority), "priority", "Choose a priority.")
                .length("address", in.address(), 5, 150, "Street address or landmark")
                .length("suburb", in.suburb(), 2, 60, "Suburb")
                .length("city", in.city(), 2, 40, "City or town")
                .require(location.length() <= 255, "address", "Location is too long.")
                .require(phone.isEmpty() || Validator.SA_PHONE.matcher(phone).matches(), "contactNumber",
                        "Enter a 10-digit contact number.")
                .throwIfInvalid();

        CreatedRequest created = requests.create(new NewServiceRequest(Validator.clean(in.title()),
                Validator.clean(in.description()), in.categoryId(), priority, actor.userId(), location));
        String contactPhone = phone.isEmpty() ? actor.phone() : phone;
        if (contactPhone != null && !contactPhone.isBlank()) {
            queries.saveContactPhone(created.requestId(), contactPhone);
        }

        publisher.publish(new StatusChangedEvent(created.requestId(), created.referenceNo(),
                Validator.clean(in.title()), actor.userId(), null, RequestStatusCode.SUBMITTED,
                actor.userId(), clock.instant(), "Request logged", contactPhone));
        return detail(actor, created.requestId());
    }

    // ------------------------------------------------------------------ read (FR-05, FR-06)

    public List<RequestViews.Summary> listMine(AuthenticatedUser actor) {
        requireSignedIn(actor);
        return queries.listByRequester(actor.userId(), LIST_LIMIT).stream().map(RequestViews.Summary::of).toList();
    }

    /** Staff see their own assignments; coordinators/managers/admins see every open request. */
    public List<RequestViews.Summary> queue(AuthenticatedUser actor, String statusFilter) {
        requireSignedIn(actor);
        if (!AccessPolicy.hasQueue(actor)) throw new ApiException.Forbidden("Only staff have a work queue.");
        String status = statusFilter == null || statusFilter.isBlank() ? null : statusFilter.strip().toUpperCase();
        if (status != null) {
            try { RequestStatusCode.fromCode(status); }
            catch (IllegalArgumentException e) { throw new ApiException.Validation("status", "Unknown status."); }
        }
        Long assignee = actor.role() == UserRole.STAFF ? actor.userId() : null;
        return queries.listOpenQueue(assignee, status, LIST_LIMIT).stream().map(RequestViews.Summary::of).toList();
    }

    public RequestViews.Detail detail(AuthenticatedUser actor, long requestId) {
        ServiceRequestRecord r = loadVisible(actor, requestId);
        RequestStatusCode status = RequestStatusCode.fromCode(r.statusCode());
        TransitionContext ctx = new TransitionContext(actor, r.requesterId(), r.assigneeId(), null, null, null);
        List<RequestViews.Action> actions = lifecycle.availableMoves(status, ctx).stream()
                .map(RequestViews.Action::of).toList();
        var feedback = queries.findFeedback(requestId).orElse(null);
        boolean canGiveFeedback = feedback == null && r.requesterId() == actor.userId()
                && (status == RequestStatusCode.RESOLVED || status == RequestStatusCode.CLOSED);
        List<RequestViews.SimulatedMessage> messages = outbox.findForRequest(requestId).stream()
                .map(RequestViews.SimulatedMessage::of).toList();
        return new RequestViews.Detail(RequestViews.Summary.of(r),
                requests.findHistory(requestId).stream().map(RequestViews.HistoryItem::of).toList(),
                actions, feedback, canGiveFeedback, messages);
    }

    // ------------------------------------------------------------------ change status (FR-07..FR-10)

    public record ChangeInput(String target, Integer expectedVersion, Long assigneeId, String note,
                              String resolutionNotes) { }

    public RequestViews.ChangeResult changeStatus(AuthenticatedUser actor, long requestId, ChangeInput in) {
        ServiceRequestRecord r = loadVisible(actor, requestId);
        RequestStatusCode target;
        try {
            target = RequestStatusCode.fromCode(Validator.clean(in.target()));
        } catch (IllegalArgumentException e) {
            throw new ApiException.Validation("target", "Unknown status.");
        }
        if (in.expectedVersion() == null) throw new ApiException.Validation("expectedVersion", "Reload the request and try again.");
        if (in.note() != null && in.note().length() > 1000) throw new ApiException.Validation("note", "Note is too long.");
        if (in.resolutionNotes() != null && in.resolutionNotes().length() > 2000) {
            throw new ApiException.Validation("resolutionNotes", "Resolution notes are too long.");
        }
        if (target == RequestStatusCode.ASSIGNED && in.assigneeId() != null) requireActiveStaff(in.assigneeId());

        RequestStatusCode current = RequestStatusCode.fromCode(r.statusCode());
        TransitionContext ctx = new TransitionContext(actor, r.requesterId(), r.assigneeId(),
                in.assigneeId(), in.note(), in.resolutionNotes());
        StatusChange change = lifecycle.plan(requestId, in.expectedVersion(), current, target, ctx);

        try {
            requests.changeStatus(change);
        } catch (StaleUpdateException e) {
            throw new ApiException.Conflict("STALE_REQUEST",
                    "Someone else changed this request while you were looking at it. Reload and try again.");
        } catch (RecordNotFoundException e) {
            throw new ApiException.NotFound("Request not found.");
        } catch (DataIntegrityException e) {
            throw new ApiException.Conflict("INTEGRITY_RULE", "The database rejected this change: " + e.getMessage());
        }

        String phone = queries.findContactPhone(requestId).orElse(null);
        List<String> warnings = publisher.publish(new StatusChangedEvent(requestId, r.referenceNo(), r.title(),
                r.requesterId(), current, target, actor.userId(), clock.instant(), change.note(), phone));
        return new RequestViews.ChangeResult(detail(actor, requestId), warnings);
    }

    // ------------------------------------------------------------------ feedback

    public RequestViews.Detail giveFeedback(AuthenticatedUser actor, long requestId, Integer rating, String comment) {
        ServiceRequestRecord r = loadVisible(actor, requestId);
        if (r.requesterId() != actor.userId()) throw new ApiException.Forbidden("Only the requester can rate this request.");
        RequestStatusCode status = RequestStatusCode.fromCode(r.statusCode());
        if (status != RequestStatusCode.RESOLVED && status != RequestStatusCode.CLOSED) {
            throw new ApiException.TransitionNotAllowed("You can rate a request once it has been resolved.");
        }
        String text = Validator.clean(comment);
        new Validator()
                .require(rating != null && rating >= 1 && rating <= 5, "rating", "Choose between 1 and 5 stars.")
                .require(text.length() <= 1000, "comment", "Comment is too long.")
                .throwIfInvalid();
        if (!queries.saveFeedback(requestId, rating, text.isEmpty() ? null : text, actor.userId())) {
            throw new ApiException.Conflict("FEEDBACK_EXISTS", "You have already rated this request.");
        }
        return detail(actor, requestId);
    }

    // ------------------------------------------------------------------ helpers

    public List<RequestQueries.Category> categories() {
        return queries.listActiveCategories();
    }

    public List<StaffMember> assignableStaff(AuthenticatedUser actor) {
        requireSignedIn(actor);
        if (!actor.role().isOversight()) throw new ApiException.Forbidden("Only coordinators can assign requests.");
        return users.findActiveStaff().stream().map(u -> new StaffMember(u.userId(), u.fullName())).toList();
    }

    public record StaffMember(long userId, String fullName) { }

    /** Not found and not allowed look the same to the caller (no reference-number probing). */
    private ServiceRequestRecord loadVisible(AuthenticatedUser actor, long requestId) {
        requireSignedIn(actor);
        ServiceRequestRecord r = requests.findById(requestId).orElse(null);
        if (r == null || !AccessPolicy.canView(actor, r)) throw new ApiException.NotFound("Request not found.");
        return r;
    }

    private void requireActiveStaff(long userId) {
        UserAccount u = users.findById(userId).orElse(null);
        if (u == null || !u.active() || u.role() != UserRole.STAFF) {
            throw new ApiException.Validation("assigneeId", "Choose an active staff member.");
        }
    }

    private static void requireSignedIn(AuthenticatedUser actor) {
        if (actor == null) throw new ApiException.Unauthenticated("Please sign in.");
    }
}
