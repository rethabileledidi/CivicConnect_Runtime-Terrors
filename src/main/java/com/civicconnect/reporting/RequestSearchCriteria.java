package com.civicconnect.reporting;

import java.time.LocalDate;
import java.util.LinkedHashSet;
import java.util.Set;

/** Search / filter / sort / paging options for request reports. Plain mutable bean. */
public class RequestSearchCriteria {

    public static final int DEFAULT_PAGE_SIZE = 25;
    public static final int MAX_PAGE_SIZE = 200;
    public static final int MAX_EXPORT_ROWS = 10_000;

    private ReportType reportType = ReportType.ALL;
    private String text;
    private String lifecycleGroup;                        // OPEN / RESOLVED / CLOSED
    private final Set<String> statusCodes = new LinkedHashSet<>();
    private final Set<Integer> categoryIds = new LinkedHashSet<>();
    private final Set<String> priorities = new LinkedHashSet<>();
    private boolean overdueOnly;
    private Long assigneeId;
    private boolean unassignedOnly;
    private LocalDate createdFrom;
    private LocalDate createdTo;                          // inclusive
    private SortField sortField = SortField.CREATED;
    private boolean ascending = false;
    private int page = 1;
    private int pageSize = DEFAULT_PAGE_SIZE;

    public static RequestSearchCriteria forReport(ReportType type) {
        RequestSearchCriteria c = new RequestSearchCriteria();
        c.setReportType(type);
        return c;
    }

    public ReportType getReportType() { return reportType; }
    public void setReportType(ReportType reportType) {
        this.reportType = reportType == null ? ReportType.ALL : reportType;
        this.reportType.applyTo(this);
    }

    public String getText() { return text; }
    public void setText(String text) { this.text = text == null || text.isBlank() ? null : text.trim(); }

    public String getLifecycleGroup() { return lifecycleGroup; }
    public void setLifecycleGroup(String lifecycleGroup) { this.lifecycleGroup = lifecycleGroup; }

    public Set<String> getStatusCodes() { return statusCodes; }
    public Set<Integer> getCategoryIds() { return categoryIds; }
    public Set<String> getPriorities() { return priorities; }

    public boolean isOverdueOnly() { return overdueOnly; }
    public void setOverdueOnly(boolean overdueOnly) { this.overdueOnly = overdueOnly; }

    public Long getAssigneeId() { return assigneeId; }
    public void setAssigneeId(Long assigneeId) { this.assigneeId = assigneeId; }

    public boolean isUnassignedOnly() { return unassignedOnly; }
    public void setUnassignedOnly(boolean unassignedOnly) { this.unassignedOnly = unassignedOnly; }

    public LocalDate getCreatedFrom() { return createdFrom; }
    public void setCreatedFrom(LocalDate createdFrom) { this.createdFrom = createdFrom; }

    public LocalDate getCreatedTo() { return createdTo; }
    public void setCreatedTo(LocalDate createdTo) { this.createdTo = createdTo; }

    public SortField getSortField() { return sortField; }
    public void setSortField(SortField sortField) { this.sortField = sortField == null ? SortField.CREATED : sortField; }

    public boolean isAscending() { return ascending; }
    public void setAscending(boolean ascending) { this.ascending = ascending; }

    public int getPage() { return page; }
    public void setPage(int page) { this.page = Math.max(1, page); }

    public int getPageSize() { return pageSize; }
    public void setPageSize(int pageSize) {
        this.pageSize = pageSize < 1 ? DEFAULT_PAGE_SIZE : Math.min(pageSize, MAX_PAGE_SIZE);
    }

    public int offset() { return (page - 1) * pageSize; }

    // Helpers for the filter form (option values arrive as strings)
    public boolean hasStatus(String code) { return statusCodes.contains(code); }
    public boolean hasPriority(String p) { return priorities.contains(p); }
    public boolean hasCategory(String id) {
        try { return categoryIds.contains(Integer.valueOf(id)); } catch (NumberFormatException e) { return false; }
    }
    public String createdFromText() { return createdFrom == null ? "" : createdFrom.toString(); }
    public String createdToText() { return createdTo == null ? "" : createdTo.toString(); }
}
