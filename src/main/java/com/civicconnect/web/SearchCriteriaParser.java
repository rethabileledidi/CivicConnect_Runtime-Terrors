package com.civicconnect.web;

import com.civicconnect.reporting.ReportType;
import com.civicconnect.reporting.RequestSearchCriteria;
import com.civicconnect.reporting.SortField;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Converts HTTP query parameters into {@link RequestSearchCriteria} and back.
 * Invalid values are ignored rather than trusted (unknown sort -> default, bad date -> no filter).
 *
 * Parameters: report, q, status*, category*, priority*, overdue=1, assignee=(id|none),
 * from=yyyy-MM-dd, to=yyyy-MM-dd, sort, dir=(asc|desc), page, size   (* = repeatable)
 */
public final class SearchCriteriaParser {

    private SearchCriteriaParser() { }

    public static RequestSearchCriteria parse(Map<String, String[]> p) {
        RequestSearchCriteria c = RequestSearchCriteria.forReport(ReportType.fromParam(first(p, "report")));
        c.setText(first(p, "q"));
        for (String s : all(p, "status")) {
            if (s.matches("[A-Z_]{1,20}")) c.getStatusCodes().add(s);
        }
        for (String s : all(p, "category")) {
            Integer id = parseInt(s);
            if (id != null) c.getCategoryIds().add(id);
        }
        for (String s : all(p, "priority")) c.getPriorities().add(s);
        if ("1".equals(first(p, "overdue")) || "true".equalsIgnoreCase(first(p, "overdue"))) c.setOverdueOnly(true);

        String assignee = first(p, "assignee");
        if ("none".equalsIgnoreCase(assignee)) {
            c.setUnassignedOnly(true);
        } else if (assignee != null) {
            Integer id = parseInt(assignee);
            if (id != null) c.setAssigneeId(id.longValue());
        }
        c.setCreatedFrom(parseDate(first(p, "from")));
        c.setCreatedTo(parseDate(first(p, "to")));
        c.setSortField(SortField.fromParam(first(p, "sort")));
        c.setAscending("asc".equalsIgnoreCase(first(p, "dir")));
        Integer page = parseInt(first(p, "page"));
        if (page != null) c.setPage(page);
        Integer size = parseInt(first(p, "size"));
        if (size != null) c.setPageSize(size);
        return c;
    }

    /** Rebuilds the query string for the current filters (used for sort, paging and export links). */
    public static String toQueryString(RequestSearchCriteria c, SortField sort, boolean ascending, int page) {
        List<String> parts = new ArrayList<>();
        add(parts, "report", c.getReportType().param());
        add(parts, "q", c.getText());
        c.getStatusCodes().forEach(s -> add(parts, "status", s));
        c.getCategoryIds().forEach(id -> add(parts, "category", String.valueOf(id)));
        c.getPriorities().forEach(s -> add(parts, "priority", s));
        if (c.isOverdueOnly() && c.getReportType() != ReportType.OVERDUE) add(parts, "overdue", "1");
        if (c.isUnassignedOnly()) add(parts, "assignee", "none");
        else if (c.getAssigneeId() != null) add(parts, "assignee", String.valueOf(c.getAssigneeId()));
        if (c.getCreatedFrom() != null) add(parts, "from", c.getCreatedFrom().toString());
        if (c.getCreatedTo() != null) add(parts, "to", c.getCreatedTo().toString());
        add(parts, "sort", sort.param());
        add(parts, "dir", ascending ? "asc" : "desc");
        if (page > 1) add(parts, "page", String.valueOf(page));
        if (c.getPageSize() != RequestSearchCriteria.DEFAULT_PAGE_SIZE) add(parts, "size", String.valueOf(c.getPageSize()));
        return String.join("&", parts);
    }

    private static void add(List<String> parts, String name, String value) {
        if (value != null && !value.isBlank()) {
            parts.add(name + "=" + URLEncoder.encode(value, StandardCharsets.UTF_8));
        }
    }

    private static String first(Map<String, String[]> p, String name) {
        String[] v = p.get(name);
        return v == null || v.length == 0 || v[0] == null || v[0].isBlank() ? null : v[0].trim();
    }

    private static List<String> all(Map<String, String[]> p, String name) {
        String[] v = p.get(name);
        List<String> out = new ArrayList<>();
        if (v != null) {
            for (String s : v) if (s != null && !s.isBlank()) out.add(s.trim().toUpperCase());
        }
        return out;
    }

    private static Integer parseInt(String s) {
        if (s == null) return null;
        try {
            return Integer.valueOf(s.trim());
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private static LocalDate parseDate(String s) {
        if (s == null) return null;
        try {
            return LocalDate.parse(s);
        } catch (DateTimeParseException e) {
            return null;
        }
    }
}
