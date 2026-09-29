package com.civicconnect.data.model;

import com.civicconnect.util.DisplayFormat;

import java.time.OffsetDateTime;

public record StatusHistoryEntry(long historyId, String fromStatus, String fromStatusName,
                                 String toStatus, String toStatusName, long changedById,
                                 String changedByName, String changedByRole, OffsetDateTime changedAt,
                                 String assigneeName, String note) {
    public String changedAtText() { return DisplayFormat.dateTime(changedAt); }
}
