package com.civicconnect.notification;

import java.time.Instant;
import java.util.List;

/** Persistence port for in-app notifications (civic.notification, V5). */
public interface NotificationRepository {

    void add(long userId, Long requestId, String title, String body);

    /** Newest first, at most {@code limit}. */
    List<Notification> listForUser(long userId, int limit);

    /** Marks every unread notification of this user as read; returns how many changed. */
    int markAllRead(long userId);

    record Notification(long notificationId, Long requestId, String title, String body,
                        Instant createdAt, boolean read) { }
}
