package com.civicconnect.notification;

import com.civicconnect.support.Fakes;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** CR-03: simulated delivery, retry and give-up behaviour. */
class OutboxDispatcherTest {

    @Test
    void pendingMessagesAreSentThroughTheMatchingChannel() {
        Fakes.Outbox outbox = new Fakes.Outbox();
        outbox.enqueue(1, OutboxRepository.Channel.SMS, "0821234567", "hello");
        outbox.enqueue(1, OutboxRepository.Channel.WHATSAPP, "0821234567", "hello");
        OutboxDispatcher d = new OutboxDispatcher(outbox, List.of(
                new MessageChannel.Simulated(OutboxRepository.Channel.SMS, 0),
                new MessageChannel.Simulated(OutboxRepository.Channel.WHATSAPP, 0)), Fakes.fixedClock());
        assertEquals(new OutboxDispatcher.Result(2, 0), d.dispatchOnce());
        assertTrue(outbox.messages.stream().allMatch(m -> m.status() == OutboxRepository.Status.SENT));
        assertEquals(new OutboxDispatcher.Result(0, 0), d.dispatchOnce());   // nothing sent twice
    }

    @Test
    void failuresAreRetriedThenMarkedFailedAfterThreeAttempts() {
        Fakes.Outbox outbox = new Fakes.Outbox();
        outbox.enqueue(1, OutboxRepository.Channel.SMS, "0821234567", "hello");
        OutboxDispatcher d = new OutboxDispatcher(outbox, List.of(
                new MessageChannel.Simulated(OutboxRepository.Channel.SMS, 1)), Fakes.fixedClock());   // always fails
        d.dispatchOnce();
        d.dispatchOnce();
        assertEquals(OutboxRepository.Status.PENDING, outbox.messages.get(0).status());
        d.dispatchOnce();
        assertEquals(OutboxRepository.Status.FAILED, outbox.messages.get(0).status());
        assertEquals(3, outbox.messages.get(0).attempts());
        assertNotNull(outbox.messages.get(0).lastError());
    }

    @Test
    void messagesForAChannelWithNoProviderFailSafely() {
        Fakes.Outbox outbox = new Fakes.Outbox();
        outbox.enqueue(1, OutboxRepository.Channel.WHATSAPP, "0821234567", "hello");
        OutboxDispatcher d = new OutboxDispatcher(outbox, List.of(), Fakes.fixedClock());
        assertEquals(new OutboxDispatcher.Result(0, 1), d.dispatchOnce());
    }

    @Test
    void logsNeverContainTheFullPhoneNumber() {
        assertEquals("******4567", MessageChannel.Simulated.mask("0821234567"));
        assertEquals("***", MessageChannel.Simulated.mask(null));
    }

    @Test
    void requesterIsNotNotifiedAboutTheirOwnChange() {
        Fakes.Notifications n = new Fakes.Notifications();
        Fakes.Outbox o = new Fakes.Outbox();
        StatusChangePublisher p = new StatusChangePublisher(List.of(
                new Listeners.InAppNotificationListener(n), new Listeners.SimulatedMessagingListener(o)));
        p.publish(new StatusChangedEvent(1, "CC-000001", "t", 7, com.civicconnect.data.model.RequestStatusCode.RESOLVED,
                com.civicconnect.data.model.RequestStatusCode.REOPENED, 7, Fakes.NOW, "came back", "0821234567"));
        assertTrue(n.items.isEmpty());
        assertTrue(o.messages.isEmpty());
    }

    @Test
    void noPhoneMeansNoSimulatedMessagesButStillAnInAppNotification() {
        Fakes.Notifications n = new Fakes.Notifications();
        Fakes.Outbox o = new Fakes.Outbox();
        new StatusChangePublisher(List.of(new Listeners.InAppNotificationListener(n), new Listeners.SimulatedMessagingListener(o)))
                .publish(new StatusChangedEvent(1, "CC-000001", "t", 7, com.civicconnect.data.model.RequestStatusCode.SUBMITTED,
                        com.civicconnect.data.model.RequestStatusCode.ASSIGNED, 4, Fakes.NOW, null, null));
        assertEquals(1, n.items.size());
        assertTrue(o.messages.isEmpty());
    }
}
