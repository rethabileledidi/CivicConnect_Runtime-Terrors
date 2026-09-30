package com.civicconnect.service;

import com.civicconnect.auth.AuthenticatedUser;
import com.civicconnect.common.ApiException;
import com.civicconnect.notification.NotificationRepository;

import java.util.List;

/** The notification bell (FR-13): a user only ever sees their own notifications. */
public final class NotificationService {

    private final NotificationRepository notifications;

    public NotificationService(NotificationRepository notifications) {
        this.notifications = notifications;
    }

    public record Inbox(List<NotificationRepository.Notification> items, long unread) { }

    public Inbox inbox(AuthenticatedUser actor) {
        if (actor == null) throw new ApiException.Unauthenticated("Please sign in.");
        List<NotificationRepository.Notification> items = notifications.listForUser(actor.userId(), 30);
        return new Inbox(items, items.stream().filter(n -> !n.read()).count());
    }

    public int markAllRead(AuthenticatedUser actor) {
        if (actor == null) throw new ApiException.Unauthenticated("Please sign in.");
        return notifications.markAllRead(actor.userId());
    }
}
