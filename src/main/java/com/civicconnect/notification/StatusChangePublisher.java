package com.civicconnect.notification;

import java.util.ArrayList;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Subject in the Observer pattern. Calls every listener in turn and isolates failures:
 * a failing listener is logged and reported as a warning, never re-thrown.
 * <p>
 * A2 identified silent subscriber failure as the main Observer risk. Two design choices contain it:
 * (1) the audit history is NOT a listener - it is written inside the status-change transaction
 *     (ADR-P3-02), and reporting reads live views (ADR-P3-03), so only non-critical reactions are here;
 * (2) failures are logged and returned to the caller, so they are visible rather than silent.
 */
public final class StatusChangePublisher {

    private static final Logger LOG = Logger.getLogger(StatusChangePublisher.class.getName());

    private final List<StatusChangeListener> listeners;

    public StatusChangePublisher(List<StatusChangeListener> listeners) {
        this.listeners = List.copyOf(listeners);
    }

    /** @return one warning per listener that failed (empty when all succeeded) */
    public List<String> publish(StatusChangedEvent event) {
        List<String> warnings = new ArrayList<>();
        for (StatusChangeListener listener : listeners) {
            try {
                listener.onStatusChanged(event);
            } catch (RuntimeException e) {
                LOG.log(Level.WARNING, "Listener " + listener.name() + " failed for " + event.referenceNo(), e);
                warnings.add(listener.name() + " could not run. The status change itself was saved.");
            }
        }
        return warnings;
    }
}
