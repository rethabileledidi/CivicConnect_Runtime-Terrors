package com.civicconnect.reporting;

import com.civicconnect.data.RequestOverviewMapper;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Builds the WHERE / ORDER BY / LIMIT for request reports from {@link RequestSearchCriteria}.
 * Pure Java (no database) so it is unit-tested directly.
 */
public final class RequestQueryBuilder {

    private static final Set<String> LIFECYCLE_GROUPS = Set.of("OPEN", "RESOLVED", "CLOSED");
    private static final Set<String> PRIORITIES = Set.of("LOW", "MEDIUM", "HIGH", "URGENT");

    private RequestQueryBuilder() { }

    public static SqlQuery count(RequestSearchCriteria c) {
        List<Object> params = new ArrayList<>();
        String where = where(c, params);
        return new SqlQuery("SELECT count(*) FROM civic.v_request_overview" + where, params);
    }

    public static SqlQuery page(RequestSearchCriteria c) {
        return select(c, c.getPageSize(), c.offset());
    }

    public static SqlQuery export(RequestSearchCriteria c) {
        return select(c, RequestSearchCriteria.MAX_EXPORT_ROWS, 0);
    }

    private static SqlQuery select(RequestSearchCriteria c, int limit, int offset) {
        List<Object> params = new ArrayList<>();
        String where = where(c, params);
        String direction = c.isAscending() ? "ASC" : "DESC";
        String nulls = c.isAscending() ? "NULLS FIRST" : "NULLS LAST";
        String sql = "SELECT " + RequestOverviewMapper.COLUMNS
                + " FROM civic.v_request_overview" + where
                + " ORDER BY " + c.getSortField().sqlExpression() + " " + direction + " " + nulls
                + ", request_id " + direction          // stable tiebreaker => deterministic paging
                + " LIMIT ? OFFSET ?";
        params.add(limit);
        params.add(offset);
        return new SqlQuery(sql, params);
    }

    static String where(RequestSearchCriteria c, List<Object> params) {
        List<String> clauses = new ArrayList<>();

        if (c.getLifecycleGroup() != null && LIFECYCLE_GROUPS.contains(c.getLifecycleGroup())) {
            clauses.add("lifecycle_group = ?");
            params.add(c.getLifecycleGroup());
        }
        if (c.isOverdueOnly()) {
            clauses.add("is_overdue");
        }
        if (!c.getStatusCodes().isEmpty()) {
            clauses.add("status_code IN (" + placeholders(c.getStatusCodes()) + ")");
            params.addAll(c.getStatusCodes());
        }
        if (!c.getCategoryIds().isEmpty()) {
            clauses.add("category_id IN (" + placeholders(c.getCategoryIds()) + ")");
            params.addAll(c.getCategoryIds());
        }
        List<String> priorities = c.getPriorities().stream()
                .map(p -> p.toUpperCase(Locale.ROOT)).filter(PRIORITIES::contains).toList();
        if (!priorities.isEmpty()) {
            clauses.add("priority IN (" + placeholders(priorities) + ")");
            params.addAll(priorities);
        }
        if (c.isUnassignedOnly()) {
            clauses.add("assignee_id IS NULL");
        } else if (c.getAssigneeId() != null) {
            clauses.add("assignee_id = ?");
            params.add(c.getAssigneeId());
        }
        if (c.getCreatedFrom() != null) {
            clauses.add("created_at >= ?");
            params.add(c.getCreatedFrom());               // date => midnight in the DB time zone
        }
        if (c.getCreatedTo() != null) {
            clauses.add("created_at < ?");
            params.add(c.getCreatedTo().plusDays(1));      // inclusive end date
        }
        if (c.getText() != null) {
            String like = "%" + escapeLike(c.getText()) + "%";
            clauses.add("(reference_no ILIKE ? OR title ILIKE ? OR description ILIKE ?"
                    + " OR location_text ILIKE ? OR requester_name ILIKE ? OR assignee_name ILIKE ?)");
            for (int i = 0; i < 6; i++) params.add(like);
        }
        return clauses.isEmpty() ? "" : " WHERE " + String.join(" AND ", clauses);
    }

    /** Escapes LIKE wildcards so a user typing % or _ searches for the literal character. */
    static String escapeLike(String s) {
        return s.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_");
    }

    private static String placeholders(Collection<?> values) {
        return String.join(", ", java.util.Collections.nCopies(values.size(), "?"));
    }
}
