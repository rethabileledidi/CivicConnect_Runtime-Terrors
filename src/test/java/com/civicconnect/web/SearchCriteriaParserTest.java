package com.civicconnect.web;

import com.civicconnect.reporting.ReportType;
import com.civicconnect.reporting.RequestSearchCriteria;
import com.civicconnect.reporting.SortField;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class SearchCriteriaParserTest {

    @Test
    void parsesAllSupportedParameters() {
        Map<String, String[]> p = new HashMap<>();
        p.put("report", new String[]{"overdue"});
        p.put("q", new String[]{"  pothole "});
        p.put("status", new String[]{"assigned", "in_progress"});
        p.put("category", new String[]{"3", "x"});
        p.put("assignee", new String[]{"none"});
        p.put("from", new String[]{"2026-09-01"});
        p.put("to", new String[]{"not-a-date"});
        p.put("sort", new String[]{"age"});
        p.put("dir", new String[]{"asc"});
        p.put("page", new String[]{"2"});

        RequestSearchCriteria c = SearchCriteriaParser.parse(p);
        assertEquals(ReportType.OVERDUE, c.getReportType());
        assertTrue(c.isOverdueOnly());
        assertEquals("OPEN", c.getLifecycleGroup());
        assertEquals("pothole", c.getText());
        assertTrue(c.hasStatus("ASSIGNED") && c.hasStatus("IN_PROGRESS"));
        assertTrue(c.hasCategory("3"));
        assertEquals(1, c.getCategoryIds().size());
        assertTrue(c.isUnassignedOnly());
        assertEquals(LocalDate.of(2026, 9, 1), c.getCreatedFrom());
        assertNull(c.getCreatedTo());
        assertEquals(SortField.AGE, c.getSortField());
        assertTrue(c.isAscending());
        assertEquals(2, c.getPage());
    }

    @Test
    void roundTripsThroughQueryString() {
        Map<String, String[]> p = new HashMap<>();
        p.put("report", new String[]{"open"});
        p.put("q", new String[]{"Main Rd & Church St"});
        p.put("category", new String[]{"2"});
        RequestSearchCriteria c = SearchCriteriaParser.parse(p);
        String qs = SearchCriteriaParser.toQueryString(c, SortField.DUE, true, 3);
        assertTrue(qs.contains("report=open"));
        assertTrue(qs.contains("q=Main+Rd+%26+Church+St"));
        assertTrue(qs.contains("category=2"));
        assertTrue(qs.contains("sort=due&dir=asc&page=3"));
    }

    @Test
    void emptyParametersGiveDefaultAllReport() {
        RequestSearchCriteria c = SearchCriteriaParser.parse(Map.of());
        assertEquals(ReportType.ALL, c.getReportType());
        assertEquals(SortField.CREATED, c.getSortField());
        assertFalse(c.isAscending());
        assertEquals(1, c.getPage());
    }
}
