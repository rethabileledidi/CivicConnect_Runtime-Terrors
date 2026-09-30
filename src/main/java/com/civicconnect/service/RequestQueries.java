package com.civicconnect.service;

import com.civicconnect.data.model.ServiceRequestRecord;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Backend-specific reads and small writes that Rethabile's reporting module does not cover:
 * a resident's own list, the staff/coordinator work queue, categories, feedback and the
 * contact number. Every request row comes from the same view (v_request_overview) as his
 * reports, so "overdue" and "open" mean the same thing on every screen (ADR-P3-03).
 */
public interface RequestQueries {

    List<ServiceRequestRecord> listByRequester(long requesterId, int limit);

    /**
     * Open work queue (Submitted, Assigned, In Progress, Reopened), overdue first, then oldest first.
     *
     * @param assigneeId only this assignee's requests, or null for all
     * @param statusCode optional status filter, or null
     */
    List<ServiceRequestRecord> listOpenQueue(Long assigneeId, String statusCode, int limit);

    List<Category> listActiveCategories();

    Optional<String> findContactPhone(long requestId);

    void saveContactPhone(long requestId, String phone);

    Optional<Feedback> findFeedback(long requestId);

    /** @return false if feedback already exists for this request */
    boolean saveFeedback(long requestId, int rating, String comment, long submittedBy);

    record Category(int categoryId, String code, String name, String description,
                    String departmentName, int slaHours) { }

    record Feedback(int rating, String comment, Instant submittedAt) { }
}
