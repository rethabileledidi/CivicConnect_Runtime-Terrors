package com.civicconnect.notification;

import java.time.Clock;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Delivers pending outbox messages through the matching channel. Runs every few seconds on a
 * background thread started by {@link com.civicconnect.api.BackendContextListener}.
 * A message is retried up to {@value #MAX_ATTEMPTS} times, then marked FAILED.
 */
public final class OutboxDispatcher implements Runnable {

    public static final int MAX_ATTEMPTS = 3;
    private static final Logger LOG = Logger.getLogger(OutboxDispatcher.class.getName());

    private final OutboxRepository outbox;
    private final Map<OutboxRepository.Channel, MessageChannel> channels = new EnumMap<>(OutboxRepository.Channel.class);
    private final Clock clock;

    public OutboxDispatcher(OutboxRepository outbox, List<MessageChannel> channels, Clock clock) {
        this.outbox = outbox;
        this.clock = clock;
        channels.forEach(ch -> this.channels.put(ch.channel(), ch));
    }

    public record Result(int sent, int failed) { }

    /** One pass over up to 50 pending messages. */
    public Result dispatchOnce() {
        int sent = 0;
        int failed = 0;
        for (OutboxRepository.OutboxMessage m : outbox.findPending(50)) {
            MessageChannel channel = channels.get(m.channel());
            try {
                if (channel == null) throw new IllegalStateException("No channel configured for " + m.channel());
                channel.send(m);
                outbox.markSent(m.messageId(), clock.instant());
                sent++;
            } catch (RuntimeException e) {
                String error = e.getMessage() == null ? e.getClass().getSimpleName() : e.getMessage();
                outbox.recordFailure(m.messageId(), error.length() > 250 ? error.substring(0, 250) : error, MAX_ATTEMPTS);
                failed++;
            }
        }
        return new Result(sent, failed);
    }

    @Override
    public void run() {
        try {
            dispatchOnce();
        } catch (RuntimeException e) {
            // Never let an exception kill the scheduled executor.
            LOG.log(Level.WARNING, "Outbox dispatch pass failed", e);
        }
    }
}
