package com.civicconnect.data.model;

import com.civicconnect.util.DisplayFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** One request as read from civic.v_request_overview (includes derived reporting fields). */
public record ServiceRequestRecord(
        long requestId, String referenceNo, String title, String description, String priority,
        String locationText, String statusCode, String statusName, String lifecycleGroup,
        int categoryId, String categoryName, String departmentName,
        long requesterId, String requesterName, Long assigneeId, String assigneeName,
        OffsetDateTime createdAt, OffsetDateTime updatedAt, OffsetDateTime dueAt,
        OffsetDateTime resolvedAt, OffsetDateTime closedAt, int version,
        boolean overdue, BigDecimal overdueHours, int ageDays, BigDecimal resolutionHours,
        Boolean resolvedWithinSla) {

    public String createdText()  { return DisplayFormat.dateTime(createdAt); }
    public String dueText()      { return DisplayFormat.dateTime(dueAt); }
    public String resolvedText() { return DisplayFormat.dateTime(resolvedAt); }
    public String closedText()   { return DisplayFormat.dateTime(closedAt); }
    public String updatedText()  { return DisplayFormat.dateTime(updatedAt); }
}
