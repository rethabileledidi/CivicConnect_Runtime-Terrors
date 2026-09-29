package com.civicconnect.data.model;

import java.util.Objects;

/** Data needed to persist a newly logged request. due_at is derived from the category SLA. */
public record NewServiceRequest(String title, String description, int categoryId,
                                String priority, long requesterId, String locationText) {
    public NewServiceRequest {
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(description, "description");
        priority = priority == null ? "MEDIUM" : priority.trim().toUpperCase();
    }
}
