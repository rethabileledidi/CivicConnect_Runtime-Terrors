package com.civicconnect.reporting;

import java.util.Locale;

/**
 * The management reports required by the brief. Each is simply a preset filter over the
 * SAME base view and the SAME lifecycle_group / is_overdue definitions, so a report can
 * never disagree with the dashboard figure that links to it.
 */
public enum ReportType {
    ALL("All requests", "All service requests"),
    OPEN("Open requests", "Submitted, assigned, in progress or reopened"),
    OVERDUE("Overdue requests", "Open requests past their category SLA due date"),
    RESOLVED("Resolved requests", "Work completed, awaiting closure"),
    CLOSED("Closed requests", "Closed or rejected (final)");

    private final String title;
    private final String description;

    ReportType(String title, String description) {
        this.title = title;
        this.description = description;
    }

    public String title() { return title; }
    public String description() { return description; }
    public String param() { return name().toLowerCase(Locale.ROOT); }

    /** Applies this report's preset to the criteria (user filters are kept on top). */
    public void applyTo(RequestSearchCriteria c) {
        switch (this) {
            case OPEN -> c.setLifecycleGroup("OPEN");
            case OVERDUE -> { c.setLifecycleGroup("OPEN"); c.setOverdueOnly(true); }
            case RESOLVED -> c.setLifecycleGroup("RESOLVED");
            case CLOSED -> c.setLifecycleGroup("CLOSED");
            case ALL -> { }
        }
    }

    public static ReportType fromParam(String value) {
        if (value == null || value.isBlank()) return ALL;
        try {
            return valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            return ALL;
        }
    }
}
