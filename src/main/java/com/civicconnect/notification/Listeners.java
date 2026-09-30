package com.civicconnect.notification;

import com.civicconnect.data.model.RequestStatusCode;
import com.civicconnect.lifecycle.RequestState;

/** The two concrete observers registered at start-up. */
public final class Listeners {

    private Listeners() { }

    /** FR-13: tells the requester their request changed (unless they made the change themselves). */
    public static final class InAppNotificationListener implements StatusChangeListener {
        private final NotificationRepository notifications;

        public InAppNotificationListener(NotificationRepository notifications) {
            this.notifications = notifications;
        }

        @Override public String name() { return "In-app notification"; }

        @Override
        public void onStatusChanged(StatusChangedEvent e) {
            if (!e.isNewRequest() && e.changedByRequester()) return;
            String title = e.isNewRequest()
                    ? "Request " + e.referenceNo() + " received"
                    : e.referenceNo() + " is now " + RequestState.display(e.toStatus());
            notifications.add(e.requesterId(), e.requestId(), title, bodyFor(e));
        }
    }

    /**
     * CR-03: queues a SIMULATED SMS and WhatsApp message in the outbox. Nothing is sent here:
     * {@link OutboxDispatcher} delivers them later through a simulated channel, so a channel
     * problem can never block or slow down a status change.
     */
    public static final class SimulatedMessagingListener implements StatusChangeListener {
        private final OutboxRepository outbox;

        public SimulatedMessagingListener(OutboxRepository outbox) {
            this.outbox = outbox;
        }

        @Override public String name() { return "SMS/WhatsApp outbox"; }

        @Override
        public void onStatusChanged(StatusChangedEvent e) {
            if (!e.isNewRequest() && e.changedByRequester()) return;
            if (e.contactPhone() == null || e.contactPhone().isBlank()) return;   // phone is optional (POPIA minimisation)
            String body = "CivicConnect: " + bodyFor(e);
            outbox.enqueue(e.requestId(), OutboxRepository.Channel.SMS, e.contactPhone(), body);
            outbox.enqueue(e.requestId(), OutboxRepository.Channel.WHATSAPP, e.contactPhone(), body);
        }
    }

    static String bodyFor(StatusChangedEvent e) {
        if (e.isNewRequest()) {
            return "Your request " + e.referenceNo() + " (" + e.title() + ") has been logged.";
        }
        return "Your request " + e.referenceNo() + " is now " + RequestState.display(e.toStatus())
                + (e.toStatus() == RequestStatusCode.RESOLVED ? ". Please confirm the fix or reopen it." : ".");
    }
}
