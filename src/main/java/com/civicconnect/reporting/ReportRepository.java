package com.civicconnect.reporting;

import com.civicconnect.data.RequestOverviewMapper;
import com.civicconnect.data.model.ServiceRequestRecord;
import com.civicconnect.reporting.model.AgeingBucket;
import com.civicconnect.reporting.model.CategoryStatusMatrix;
import com.civicconnect.reporting.model.CategorySummary;
import com.civicconnect.reporting.model.DashboardKpis;
import com.civicconnect.reporting.model.LookupOption;
import com.civicconnect.reporting.model.MonthlyTrendPoint;
import com.civicconnect.reporting.model.StatusCount;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Read-only queries over the reporting views (V2__reporting_views.sql).
 * Methods take a Connection so {@link ReportService} can run several of them inside one
 * snapshot transaction. No report computes its own definition of open/overdue/etc.
 */
public class ReportRepository {

    public DashboardKpis kpis(Connection c) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM civic.v_dashboard_kpis");
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return new DashboardKpis(
                    rs.getLong("total_requests"), rs.getLong("open_requests"), rs.getLong("overdue_requests"),
                    rs.getLong("resolved_requests"), rs.getLong("closed_requests"), rs.getLong("rejected_requests"),
                    rs.getLong("unassigned_requests"), rs.getLong("created_last_7_days"),
                    rs.getLong("resolved_last_7_days"), rs.getBigDecimal("avg_resolution_hours"),
                    rs.getBigDecimal("sla_compliance_pct"), rs.getObject("generated_at", OffsetDateTime.class));
        }
    }

    public List<StatusCount> statusCounts(Connection c) throws SQLException {
        List<StatusCount> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM civic.v_report_status_summary ORDER BY sort_order");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new StatusCount(rs.getString("status_code"), rs.getString("display_name"),
                        rs.getString("lifecycle_group"), rs.getLong("request_count"), rs.getLong("overdue_count")));
            }
        }
        return list;
    }

    public List<CategorySummary> categorySummaries(Connection c) throws SQLException {
        List<CategorySummary> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM civic.v_report_category_summary ORDER BY total_requests DESC, category_name");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new CategorySummary(rs.getInt("category_id"), rs.getString("category_code"),
                        rs.getString("category_name"), rs.getString("department_name"), rs.getInt("sla_hours"),
                        rs.getLong("total_requests"), rs.getLong("open_requests"), rs.getLong("overdue_requests"),
                        rs.getLong("resolved_requests"), rs.getLong("closed_requests"),
                        rs.getBigDecimal("avg_resolution_hours"), rs.getBigDecimal("sla_compliance_pct")));
            }
        }
        return list;
    }

    public CategoryStatusMatrix categoryStatusMatrix(Connection c, List<StatusCount> columns) throws SQLException {
        Map<Integer, String> names = new LinkedHashMap<>();
        Map<Integer, Map<String, Long>> counts = new LinkedHashMap<>();
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM civic.v_report_category_status ORDER BY category_name, status_sort_order");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                int id = rs.getInt("category_id");
                names.putIfAbsent(id, rs.getString("category_name"));
                counts.computeIfAbsent(id, k -> new LinkedHashMap<>())
                      .put(rs.getString("status_code"), rs.getLong("request_count"));
            }
        }
        List<CategoryStatusMatrix.Row> rows = new ArrayList<>();
        long max = 0;
        for (Map.Entry<Integer, String> e : names.entrySet()) {
            Map<String, Long> byStatus = counts.get(e.getKey());
            long total = byStatus.values().stream().mapToLong(Long::longValue).sum();
            max = Math.max(max, byStatus.values().stream().mapToLong(Long::longValue).max().orElse(0));
            rows.add(new CategoryStatusMatrix.Row(e.getKey(), e.getValue(), byStatus, total));
        }
        return new CategoryStatusMatrix(rows, columns, max);
    }

    public List<AgeingBucket> openAgeing(Connection c) throws SQLException {
        List<AgeingBucket> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM civic.v_report_open_ageing ORDER BY bucket_order");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new AgeingBucket(rs.getInt("bucket_order"), rs.getString("bucket_label"),
                        rs.getLong("open_requests"), rs.getLong("overdue_requests")));
            }
        }
        return list;
    }

    public List<MonthlyTrendPoint> monthlyTrend(Connection c) throws SQLException {
        List<MonthlyTrendPoint> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement("SELECT * FROM civic.v_report_monthly_trend ORDER BY month_start");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                list.add(new MonthlyTrendPoint(rs.getString("month_label"),
                        rs.getLong("created_count"), rs.getLong("resolved_count")));
            }
        }
        return list;
    }

    public long countRequests(Connection c, RequestSearchCriteria criteria) throws SQLException {
        try (PreparedStatement ps = RequestQueryBuilder.count(criteria).prepare(c);
             ResultSet rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        }
    }

    public List<ServiceRequestRecord> findRequests(Connection c, SqlQuery query) throws SQLException {
        List<ServiceRequestRecord> list = new ArrayList<>();
        try (PreparedStatement ps = query.prepare(c);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(RequestOverviewMapper.map(rs));
        }
        return list;
    }

    public List<LookupOption> statusOptions(Connection c) throws SQLException {
        return options(c, "SELECT status_code, display_name FROM civic.request_status ORDER BY sort_order");
    }

    public List<LookupOption> categoryOptions(Connection c) throws SQLException {
        return options(c, "SELECT category_id::text, name FROM civic.request_category ORDER BY name");
    }

    public List<LookupOption> assigneeOptions(Connection c) throws SQLException {
        return options(c, "SELECT user_id::text, full_name FROM civic.app_user"
                + " WHERE role_code IN ('STAFF', 'COORDINATOR') AND is_active ORDER BY full_name");
    }

    private static List<LookupOption> options(Connection c, String sql) throws SQLException {
        List<LookupOption> list = new ArrayList<>();
        try (PreparedStatement ps = c.prepareStatement(sql); ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(new LookupOption(rs.getString(1), rs.getString(2)));
        }
        return list;
    }
}
