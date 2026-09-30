package com.civicconnect.notification;

import com.civicconnect.data.TransactionRunner;

import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/** JDBC implementations of the notification and outbox ports. All statements are parameterised. */
public final class JdbcNotificationStores {

    private JdbcNotificationStores() { }

    public static final class Notifications implements NotificationRepository {
        private final TransactionRunner tx;

        public Notifications(TransactionRunner tx) { this.tx = tx; }

        @Override
        public void add(long userId, Long requestId, String title, String body) {
            tx.inTransaction(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO civic.notification (user_id, request_id, title, body) VALUES (?, ?, ?, ?)")) {
                    ps.setLong(1, userId);
                    if (requestId == null) ps.setNull(2, Types.BIGINT); else ps.setLong(2, requestId);
                    ps.setString(3, truncate(title, 150));
                    ps.setString(4, truncate(body, 500));
                    return ps.executeUpdate();
                }
            });
        }

        @Override
        public List<Notification> listForUser(long userId, int limit) {
            return tx.readOnlySnapshot(c -> {
                List<Notification> list = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement("""
                        SELECT notification_id, request_id, title, body, created_at, read_at IS NOT NULL AS is_read
                          FROM civic.notification
                         WHERE user_id = ?
                         ORDER BY created_at DESC, notification_id DESC
                         LIMIT ?""")) {
                    ps.setLong(1, userId);
                    ps.setInt(2, limit);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) {
                            long req = rs.getLong("request_id");
                            Long requestId = rs.wasNull() ? null : req;
                            list.add(new Notification(rs.getLong("notification_id"), requestId,
                                    rs.getString("title"), rs.getString("body"),
                                    rs.getTimestamp("created_at").toInstant(), rs.getBoolean("is_read")));
                        }
                    }
                }
                return list;
            });
        }

        @Override
        public int markAllRead(long userId) {
            return tx.inTransaction(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE civic.notification SET read_at = now() WHERE user_id = ? AND read_at IS NULL")) {
                    ps.setLong(1, userId);
                    return ps.executeUpdate();
                }
            });
        }
    }

    public static final class Outbox implements OutboxRepository {
        private static final String COLUMNS =
                "message_id, request_id, channel, recipient, body, status, attempts, created_at, sent_at, last_error";
        private final TransactionRunner tx;

        public Outbox(TransactionRunner tx) { this.tx = tx; }

        @Override
        public void enqueue(long requestId, Channel channel, String recipient, String body) {
            tx.inTransaction(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "INSERT INTO civic.outbox_message (request_id, channel, recipient, body) VALUES (?, ?, ?, ?)")) {
                    ps.setLong(1, requestId);
                    ps.setString(2, channel.name());
                    ps.setString(3, recipient);
                    ps.setString(4, truncate(body, 500));
                    return ps.executeUpdate();
                }
            });
        }

        @Override
        public List<OutboxMessage> findPending(int limit) {
            return query("SELECT " + COLUMNS + " FROM civic.outbox_message WHERE status = 'PENDING' "
                    + "ORDER BY created_at, message_id LIMIT ?", limit);
        }

        @Override
        public void markSent(long messageId, Instant sentAt) {
            tx.inTransaction(c -> {
                try (PreparedStatement ps = c.prepareStatement(
                        "UPDATE civic.outbox_message SET status = 'SENT', sent_at = ?, attempts = attempts + 1 "
                                + "WHERE message_id = ? AND status = 'PENDING'")) {
                    ps.setTimestamp(1, Timestamp.from(sentAt));
                    ps.setLong(2, messageId);
                    return ps.executeUpdate();
                }
            });
        }

        @Override
        public void recordFailure(long messageId, String error, int maxAttempts) {
            tx.inTransaction(c -> {
                try (PreparedStatement ps = c.prepareStatement("""
                        UPDATE civic.outbox_message
                           SET attempts   = attempts + 1,
                               last_error = ?,
                               status     = CASE WHEN attempts + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END
                         WHERE message_id = ? AND status = 'PENDING'""")) {
                    ps.setString(1, error);
                    ps.setInt(2, maxAttempts);
                    ps.setLong(3, messageId);
                    return ps.executeUpdate();
                }
            });
        }

        @Override
        public List<OutboxMessage> findForRequest(long requestId) {
            return query("SELECT " + COLUMNS + " FROM civic.outbox_message WHERE request_id = ? "
                    + "ORDER BY created_at, message_id", requestId);
        }

        private List<OutboxMessage> query(String sql, Object param) {
            return tx.readOnlySnapshot(c -> {
                List<OutboxMessage> list = new ArrayList<>();
                try (PreparedStatement ps = c.prepareStatement(sql)) {
                    ps.setObject(1, param);
                    try (ResultSet rs = ps.executeQuery()) {
                        while (rs.next()) list.add(map(rs));
                    }
                }
                return list;
            });
        }

        private static OutboxMessage map(ResultSet rs) throws SQLException {
            Timestamp sent = rs.getTimestamp("sent_at");
            return new OutboxMessage(rs.getLong("message_id"), rs.getLong("request_id"),
                    Channel.valueOf(rs.getString("channel")), rs.getString("recipient"), rs.getString("body"),
                    Status.valueOf(rs.getString("status")), rs.getInt("attempts"),
                    rs.getTimestamp("created_at").toInstant(), sent == null ? null : sent.toInstant(),
                    rs.getString("last_error"));
        }
    }

    static String truncate(String s, int max) {
        return s == null || s.length() <= max ? s : s.substring(0, max);
    }
}
