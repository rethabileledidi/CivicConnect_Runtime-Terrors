package com.civicconnect.reporting;

import java.util.Locale;

/**
 * Whitelist of sortable columns. User input selects an enum constant; only the fixed SQL
 * expression below ever reaches the query, so sort parameters cannot inject SQL.
 */
public enum SortField {
    REFERENCE("reference_no"),
    TITLE("lower(title)"),
    CATEGORY("category_name"),
    STATUS("status_sort_order"),
    PRIORITY("CASE priority WHEN 'URGENT' THEN 4 WHEN 'HIGH' THEN 3 WHEN 'MEDIUM' THEN 2 ELSE 1 END"),
    CREATED("created_at"),
    DUE("due_at"),
    AGE("age_days"),
    OVERDUE("overdue_hours"),
    ASSIGNEE("assignee_name"),
    UPDATED("updated_at");

    private final String sqlExpression;

    SortField(String sqlExpression) {
        this.sqlExpression = sqlExpression;
    }

    public String sqlExpression() {
        return sqlExpression;
    }

    public String param() {
        return name().toLowerCase(Locale.ROOT);
    }

    /** Unknown or missing values fall back to CREATED (never an error, never raw SQL). */
    public static SortField fromParam(String value) {
        if (value == null || value.isBlank()) return CREATED;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return CREATED;
        }
    }
}
