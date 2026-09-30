package com.civicconnect.notification;

import java.time.Instant;
import java.util.List;

/** Persistence port for simulated SMS/WhatsApp messages (civic.outbox_message, V5). */
public interface OutboxRepository {

    enum Channel { SMS, WHATSAPP }

    enum Status { PENDING, SENT, FAILED }

    void enqueue(long requestId, Channel channel, String recipient, String body);

    /** Oldest pending messages first. */
    List<OutboxMessage> findPending(int limit);

    void markSent(long messageId, Instant sentAt);

    /** Records a failed attempt; after {@code maxAttempts} the message becomes FAILED. */
    void recordFailure(long messageId, String error, int maxAttempts);

    /** All simulated messages for one request, oldest first (Message Centre). */
    List<OutboxMessage> findForRequest(long requestId);

    record OutboxMessage(long messageId, long requestId, Channel channel, String recipient, String body,
                         Status status, int attempts, Instant createdAt, Instant sentAt, String lastError) { }
}
