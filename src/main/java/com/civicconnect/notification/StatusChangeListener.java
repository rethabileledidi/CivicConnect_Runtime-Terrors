package com.civicconnect.notification;

/** OBSERVER PATTERN (PED DP-2, ADR-06; A2 Task 1 problem P2): reacts to a committed status change. */
public interface StatusChangeListener {

    /** Short name used in logs and warnings. */
    String name();

    void onStatusChanged(StatusChangedEvent event);
}
