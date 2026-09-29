package com.civicconnect.data;

import com.civicconnect.data.model.ServiceRequestRecord;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;

/** Single mapping from civic.v_request_overview to {@link ServiceRequestRecord}, shared by all readers. */
public final class RequestOverviewMapper {

    /** Column list used by every query that returns request rows (keeps SELECTs consistent). */
    public static final String COLUMNS = """
            request_id, reference_no, title, description, priority, location_text,
            status_code, status_name, lifecycle_group, category_id, category_name, department_name,
            requester_id, requester_name, assignee_id, assignee_name,
            created_at, updated_at, due_at, resolved_at, closed_at, version,
            is_overdue, overdue_hours, age_days, resolution_hours, resolved_within_sla""";

    private RequestOverviewMapper() { }

    public static ServiceRequestRecord map(ResultSet rs) throws SQLException {
        long assignee = rs.getLong("assignee_id");
        Long assigneeId = rs.wasNull() ? null : assignee;
        boolean withinSla = rs.getBoolean("resolved_within_sla");
        Boolean resolvedWithinSla = rs.wasNull() ? null : withinSla;
        return new ServiceRequestRecord(
                rs.getLong("request_id"),
                rs.getString("reference_no"),
                rs.getString("title"),
                rs.getString("description"),
                rs.getString("priority"),
                rs.getString("location_text"),
                rs.getString("status_code"),
                rs.getString("status_name"),
                rs.getString("lifecycle_group"),
                rs.getInt("category_id"),
                rs.getString("category_name"),
                rs.getString("department_name"),
                rs.getLong("requester_id"),
                rs.getString("requester_name"),
                assigneeId,
                rs.getString("assignee_name"),
                rs.getObject("created_at", OffsetDateTime.class),
                rs.getObject("updated_at", OffsetDateTime.class),
                rs.getObject("due_at", OffsetDateTime.class),
                rs.getObject("resolved_at", OffsetDateTime.class),
                rs.getObject("closed_at", OffsetDateTime.class),
                rs.getInt("version"),
                rs.getBoolean("is_overdue"),
                rs.getBigDecimal("overdue_hours"),
                rs.getInt("age_days"),
                rs.getBigDecimal("resolution_hours"),
                resolvedWithinSla);
    }
}
