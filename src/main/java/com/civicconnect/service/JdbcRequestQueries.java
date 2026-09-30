package com.civicconnect.service;

import com.civicconnect.data.DataIntegrityException;
import com.civicconnect.data.RequestOverviewMapper;
import com.civicconnect.data.TransactionRunner;
import com.civicconnect.data.model.ServiceRequestRecord;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Types;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** JDBC implementation of {@link RequestQueries}. Reuses Rethabile's view and row mapper. */
public final class JdbcRequestQueries implements RequestQueries {

    private final TransactionRunner tx;

    public JdbcRequestQueries(TransactionRunner tx) {
        this.tx = tx;
    }

    @Override
    public List<ServiceRequestRecord> listByRequester(long requesterId, int limit) {
        return tx.readOnlySnapshot(c -> {
            try (PreparedStatement ps = c.prepareStatement("SELECT " + RequestOverviewMapper.COLUMNS
                    + " FROM civic.v_request_overview WHERE requester_id = ? ORDER BY created_at DESC, request_id DESC LIMIT ?")) {
                ps.setLong(1, requesterId);
                ps.setInt(2, limit);
                return readAll(ps);
            }
        });
    }

    @Override
    public List<ServiceRequestRecord> listOpenQueue(Long assigneeId, String statusCode, int limit) {
        String sql = "SELECT " + RequestOverviewMapper.COLUMNS + """
                  FROM civic.v_request_overview
                 WHERE lifecycle_group = 'OPEN'
                   AND (CAST(? AS BIGINT) IS NULL OR assignee_id = ?)
                   AND (CAST(? AS VARCHAR) IS NULL OR status_code = ?)
                 ORDER BY is_overdue DESC, due_at, request_id
                 LIMIT ?""";
        return tx.readOnlySnapshot(c -> {
            try (PreparedStatement ps = c.prepareStatement(sql)) {
                if (assigneeId == null) { ps.setNull(1, Types.BIGINT); ps.setNull(2, Types.BIGINT); }
                else { ps.setLong(1, assigneeId); ps.setLong(2, assigneeId); }
                if (statusCode == null) { ps.setNull(3, Types.VARCHAR); ps.setNull(4, Types.VARCHAR); }
                else { ps.setString(3, statusCode); ps.setString(4, statusCode); }
                ps.setInt(5, limit);
                return readAll(ps);
            }
        });
    }

    @Override
    public List<Category> listActiveCategories() {
        return tx.readOnlySnapshot(c -> {
            List<Category> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement("""
                    SELECT c.category_id, c.code, c.name, c.description, d.name AS department_name, c.sla_hours
                      FROM civic.request_category c
                      JOIN civic.department d ON d.department_id = c.department_id
                     WHERE c.is_active
                     ORDER BY c.name""");
                 ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(new Category(rs.getInt("category_id"), rs.getString("code"), rs.getString("name"),
                            rs.getString("description"), rs.getString("department_name"), rs.getInt("sla_hours")));
                }
            }
            return list;
        });
    }

    @Override
    public Optional<String> findContactPhone(long requestId) {
        return tx.readOnlySnapshot(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT contact_phone FROM civic.service_request WHERE request_id = ?")) {
                ps.setLong(1, requestId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next() ? Optional.ofNullable(rs.getString(1)) : Optional.empty();
                }
            }
        });
    }

    @Override
    public void saveContactPhone(long requestId, String phone) {
        tx.inTransaction(c -> {
            // Touches only contact_phone: status, version and history are untouched.
            try (PreparedStatement ps = c.prepareStatement(
                    "UPDATE civic.service_request SET contact_phone = ? WHERE request_id = ?")) {
                ps.setString(1, phone);
                ps.setLong(2, requestId);
                return ps.executeUpdate();
            }
        });
    }

    @Override
    public Optional<Feedback> findFeedback(long requestId) {
        return tx.readOnlySnapshot(c -> {
            try (PreparedStatement ps = c.prepareStatement(
                    "SELECT rating, comment, submitted_at FROM civic.request_feedback WHERE request_id = ?")) {
                ps.setLong(1, requestId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next()
                            ? Optional.of(new Feedback(rs.getInt(1), rs.getString(2), rs.getTimestamp(3).toInstant()))
                            : Optional.empty();
                }
            }
        });
    }

    @Override
    public boolean saveFeedback(long requestId, int rating, String comment, long submittedBy) {
        try {
            return tx.inTransaction(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO civic.request_feedback (request_id, rating, comment, submitted_by) VALUES (?, ?, ?, ?)")) {
                    ps.setLong(1, requestId);
                    ps.setInt(2, rating);
                    ps.setString(3, comment);
                    ps.setLong(4, submittedBy);
                    ps.executeUpdate();
                    return true;
                }
            });
        } catch (DataIntegrityException e) {
            if ("23505".equals(e.getSqlState())) return false;   // primary key: already rated
            throw e;
        }
    }

    private static List<ServiceRequestRecord> readAll(PreparedStatement ps) throws java.sql.SQLException {
        List<ServiceRequestRecord> list = new ArrayList<>();
        try (ResultSet rs = ps.executeQuery()) {
            while (rs.next()) list.add(RequestOverviewMapper.map(rs));
        }
        return list;
    }
}
