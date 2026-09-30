package com.civicconnect.service;

import com.civicconnect.data.model.ServiceRequestRecord;
import com.civicconnect.data.model.StatusHistoryEntry;
import com.civicconnect.lifecycle.RequestState;
import com.civicconnect.notification.OutboxRepository;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.List;

/**
 * Read models returned by the services and serialised to JSON by the API layer.
 * They contain only what the screen needs (no password hashes, no internal flags).
 */
public final class RequestViews {

    private RequestViews() { }

    public record Summary(long id, String reference, String title, String description,
                          int categoryId, String categoryName, String departmentName,
                          String status, String statusLabel, String lifecycleGroup,
                          String priority, String location,
                          String requesterName, Long assigneeId, String assigneeName,
                          OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime dueAt,
                          boolean overdue, int version) {

        public static Summary of(ServiceRequestRecord r) {
            return new Summary(r.requestId(), r.referenceNo(), r.title(), r.description(),
                    r.categoryId(), r.categoryName(), r.departmentName(),
                    r.statusCode(), r.statusName(), r.lifecycleGroup(),
                    r.priority(), r.locationText(),
                    r.requesterName(), r.assigneeId(), r.assigneeName(),
                    r.createdAt(), r.updatedAt(), r.dueAt(), r.overdue(), r.version());
        }
    }

    public record HistoryItem(String fromStatus, String toStatus, String toStatusLabel, String changedBy,
                              String changedByRole, OffsetDateTime changedAt, String assigneeName, String note) {
        public static HistoryItem of(StatusHistoryEntry h) {
            return new HistoryItem(h.fromStatus(), h.toStatus(), h.toStatusName(), h.changedByName(),
                    h.changedByRole(), h.changedAt(), h.assigneeName(), h.note());
        }
    }

    /** One button the UI may show, straight from the State pattern. */
    public record Action(String target, String label, boolean needsAssignee, boolean needsNote,
                         boolean needsResolutionNotes) {
        public static Action of(RequestState.Move m) {
            return new Action(m.target().name(), m.label(), m.needsAssignee(), m.needsNote(), m.needsResolutionNotes());
        }
    }

    public record SimulatedMessage(String channel, String recipient, String body, String status,
                                   int attempts, Instant createdAt, Instant sentAt) {
        public static SimulatedMessage of(OutboxRepository.OutboxMessage m) {
            String masked = m.recipient().length() < 4 ? "***" : "******" + m.recipient().substring(m.recipient().length() - 4);
            return new SimulatedMessage(m.channel().name(), masked, m.body(), m.status().name(),
                    m.attempts(), m.createdAt(), m.sentAt());
        }
    }

    public record Detail(Summary request, List<HistoryItem> history, List<Action> actions,
                         RequestQueries.Feedback feedback, boolean canGiveFeedback,
                         List<SimulatedMessage> messages) { }

    public record ChangeResult(Detail request, List<String> warnings) { }
}
