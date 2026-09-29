package com.civicconnect.data;

import com.civicconnect.data.model.CreatedRequest;
import com.civicconnect.data.model.NewServiceRequest;
import com.civicconnect.data.model.RequestStatusCode;
import com.civicconnect.data.model.ServiceRequestRecord;
import com.civicconnect.data.model.StatusChange;
import com.civicconnect.data.model.StatusHistoryEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Types;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * JDBC implementation. Every statement is parameterised (no string concatenation of
 * user input). Transactions are owned by {@link TransactionRunner}.
 */
public class JdbcServiceRequestRepository implements ServiceRequestRepository {

    private static final String INSERT_REQUEST = """
            INSERT INTO civic.service_request
                   (title, description, category_id, priority, requester_id, location_text)
            VALUES (?, ?, ?, ?, ?, ?)
            RETURNING request_id, reference_no, version""";

    private static final String INSERT_HISTORY = """
            INSERT INTO civic.request_status_history
                   (request_id, from_status, to_status, changed_by, assignee_id, note)
            VALUES (?, ?, ?, ?, ?, ?)""";

    /**
     * Optimistic concurrency: the WHERE clause only matches if nobody else changed the
     * request since the caller read it (same version AND same status).
     */
    private static final String UPDATE_STATUS = """
            UPDATE civic.service_request
               SET status_code      = ?,
                   assignee_id      = COALESCE(?, assignee_id),
                   resolved_at      = CASE WHEN ? = 'RESOLVED' THEN now()
                                           WHEN ? = 'REOPENED' THEN NULL
                                           ELSE resolved_at END,
                   resolution_notes = CASE WHEN ? = 'RESOLVED' THEN ?
                                           WHEN ? = 'REOPENED' THEN NULL
                                           ELSE resolution_notes END,
                   closed_at        = CASE WHEN ? IN ('CLOSED', 'REJECTED') THEN now()
                                           WHEN ? = 'REOPENED' THEN NULL
                                           ELSE closed_at END,
                   version          = version + 1
             WHERE request_id = ? AND version = ? AND status_code = ?
            RETURNING version, assignee_id""";

    private static final String SELECT_HISTORY = """
            SELECT h.history_id, h.from_status, fs.display_name AS from_name,
                   h.to_status, ts.display_name AS to_name,
                   h.changed_by, u.full_name AS changed_by_name, u.role_code,
                   h.changed_at, a.full_name AS assignee_name, h.note
              FROM civic.request_status_history h
              JOIN civic.request_status ts ON ts.status_code = h.to_status
              LEFT JOIN civic.request_status fs ON fs.status_code = h.from_status
              JOIN civic.app_user u ON u.user_id = h.changed_by
              LEFT JOIN civic.app_user a ON a.user_id = h.assignee_id
             WHERE h.request_id = ?
             ORDER BY h.changed_at, h.history_id""";

    private final TransactionRunner tx;

    public JdbcServiceRequestRepository(TransactionRunner tx) {
        this.tx = tx;
    }

    @Override
    public CreatedRequest create(NewServiceRequest r) {
        return tx.inTransaction(c -> {
            CreatedRequest created;
            try (PreparedStatement ps = c.prepareStatement(INSERT_REQUEST)) {
                ps.setString(1, r.title().trim());
                ps.setString(2, r.description().trim());
                ps.setInt(3, r.categoryId());
                ps.setString(4, r.priority());
                ps.setLong(5, r.requesterId());
                ps.setString(6, r.locationText());
                try (ResultSet rs = ps.executeQuery()) {
                    rs.next();
                    created = new CreatedRequest(rs.getLong(1), rs.getString(2), rs.getInt(3));
                }
            }
            insertHistory(c, created.requestId(), null, RequestStatusCode.SUBMITTED.name(),
                    r.requesterId(), null, "Request logged");
            return created;
        });
    }

    @Override
    public Optional<ServiceRequestRecord> findById(long requestId) {
        return tx.readOnlySnapshot(c -> findOne(c, "request_id = ?", requestId));
    }

    @Override
    public Optional<ServiceRequestRecord> findByReference(String referenceNo) {
        return tx.readOnlySnapshot(c -> findOne(c, "reference_no = ?", referenceNo.trim().toUpperCase()));
    }

    @Override
    public int changeStatus(StatusChange ch) {
        return tx.inTransaction(c -> {
            String to = ch.toStatus().name();
            int newVersion;
            Long assigneeAfter;
            try (PreparedStatement ps = c.prepareStatement(UPDATE_STATUS)) {
                int i = 1;
                ps.setString(i++, to);
                setNullableLong(ps, i++, ch.assigneeId());
                ps.setString(i++, to);
                ps.setString(i++, to);
                ps.setString(i++, to);
                ps.setString(i++, ch.resolutionNotes());
                ps.setString(i++, to);
                ps.setString(i++, to);
                ps.setString(i++, to);
                ps.setLong(i++, ch.requestId());
                ps.setInt(i++, ch.expectedVersion());
                ps.setString(i, ch.fromStatus().name());
                try (ResultSet rs = ps.executeQuery()) {
                    if (!rs.next()) {
                        throw explainNoRowUpdated(c, ch);
                    }
                    newVersion = rs.getInt(1);
                    long a = rs.getLong(2);
                    assigneeAfter = rs.wasNull() ? null : a;
                }
            }
            // Same transaction: if this insert fails, the status update above is rolled back.
            insertHistory(c, ch.requestId(), ch.fromStatus().name(), to, ch.changedBy(), assigneeAfter, ch.note());
            return newVersion;
        });
    }

    @Override
    public List<StatusHistoryEntry> findHistory(long requestId) {
        return tx.readOnlySnapshot(c -> {
            List<StatusHistoryEntry> list = new ArrayList<>();
            try (PreparedStatement ps = c.prepareStatement(SELECT_HISTORY)) {
                ps.setLong(1, requestId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(new StatusHistoryEntry(
                                rs.getLong("history_id"),
                                rs.getString("from_status"),
                                rs.getString("from_name"),
                                rs.getString("to_status"),
                                rs.getString("to_name"),
                                rs.getLong("changed_by"),
                                rs.getString("changed_by_name"),
                                rs.getString("role_code"),
                                rs.getObject("changed_at", OffsetDateTime.class),
                                rs.getString("assignee_name"),
                                rs.getString("note")));
                    }
                }
            }
            return list;
        });
    }

    // ------------------------------------------------------------------ helpers

    private static Optional<ServiceRequestRecord> findOne(Connection c, String where, Object key) throws SQLException {
        String sql = "SELECT " + RequestOverviewMapper.COLUMNS + " FROM civic.v_request_overview WHERE " + where;
        try (PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setObject(1, key);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(RequestOverviewMapper.map(rs)) : Optional.empty();
            }
        }
    }

    private static void insertHistory(Connection c, long requestId, String from, String to,
                                      long changedBy, Long assigneeId, String note) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(INSERT_HISTORY)) {
            ps.setLong(1, requestId);
            ps.setString(2, from);
            ps.setString(3, to);
            ps.setLong(4, changedBy);
            setNullableLong(ps, 5, assigneeId);
            ps.setString(6, note);
            ps.executeUpdate();
        }
    }

    private static DataAccessException explainNoRowUpdated(Connection c, StatusChange ch) throws SQLException {
        try (PreparedStatement ps = c.prepareStatement(
                "SELECT version, status_code FROM civic.service_request WHERE request_id = ?")) {
            ps.setLong(1, ch.requestId());
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new RecordNotFoundException("Service request " + ch.requestId() + " does not exist");
                }
                return new StaleUpdateException("Request " + ch.requestId() + " was changed by someone else"
                        + " (expected " + ch.fromStatus() + " v" + ch.expectedVersion()
                        + ", found " + rs.getString(2) + " v" + rs.getInt(1) + "). Reload and try again.");
            }
        }
    }

    private static void setNullableLong(PreparedStatement ps, int index, Long value) throws SQLException {
        if (value == null) ps.setNull(index, Types.BIGINT);
        else ps.setLong(index, value);
    }
}
