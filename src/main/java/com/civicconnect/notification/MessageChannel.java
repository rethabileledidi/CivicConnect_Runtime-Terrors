package com.civicconnect.notification;

/**
 * A way of delivering an outbox message. M2/M3 only have simulated channels (CR-03).
 * A real SMS or WhatsApp provider later is one new class implementing this interface;
 * the lifecycle, listeners and outbox table do not change (ASR-05 extensibility).
 */
public interface MessageChannel {

    OutboxRepository.Channel channel();

    /** Delivers the message or throws; must not block for long. */
    void send(OutboxRepository.OutboxMessage message);

    /** Simulated channel: "delivers" by logging. It can be told to fail every Nth call for demos/tests. */
    final class Simulated implements MessageChannel {
        private final OutboxRepository.Channel channel;
        private final int failEveryNth;
        private int calls;

        public Simulated(OutboxRepository.Channel channel, int failEveryNth) {
            this.channel = channel;
            this.failEveryNth = failEveryNth;
        }

        @Override public OutboxRepository.Channel channel() { return channel; }

        @Override
        public synchronized void send(OutboxRepository.OutboxMessage m) {
            calls++;
            if (failEveryNth > 0 && calls % failEveryNth == 0) {
                throw new IllegalStateException("Simulated " + channel + " provider unavailable");
            }
            java.util.logging.Logger.getLogger(Simulated.class.getName())
                    .info(() -> "[SIMULATED " + channel + "] to " + mask(m.recipient()) + ": " + m.body());
        }

        /** Logs never show the full phone number (POPIA). */
        static String mask(String phone) {
            return phone == null || phone.length() < 4 ? "***" : "******" + phone.substring(phone.length() - 4);
        }
    }
}
