package com.civicconnect.reporting;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Unit tests: no database needed. */
class RequestQueryBuilderTest {

    @Test
    void openReportFiltersOnLifecycleGroupOnly() {
        SqlQuery q = RequestQueryBuilder.count(RequestSearchCriteria.forReport(ReportType.OPEN));
        assertTrue(q.sql().contains("lifecycle_group = ?"));
        assertFalse(q.sql().contains("is_overdue"));
        assertEquals(List.of("OPEN"), q.params());
    }

    @Test
    void overdueReportIsOpenAndOverdue() {
        SqlQuery q = RequestQueryBuilder.count(RequestSearchCriteria.forReport(ReportType.OVERDUE));
        assertTrue(q.sql().contains("lifecycle_group = ?"));
        assertTrue(q.sql().contains("is_overdue"));
    }

    @Test
    void allReportHasNoWhereClause() {
        SqlQuery q = RequestQueryBuilder.count(RequestSearchCriteria.forReport(ReportType.ALL));
        assertFalse(q.sql().contains("WHERE"));
        assertTrue(q.params().isEmpty());
    }

    @Test
    void unknownSortFallsBackToCreatedAndNeverReachesSql() {
        RequestSearchCriteria c = new RequestSearchCriteria();
        c.setSortField(SortField.fromParam("title; DROP TABLE civic.service_request --"));
        SqlQuery q = RequestQueryBuilder.page(c);
        assertEquals(SortField.CREATED, c.getSortField());
        assertFalse(q.sql().contains("DROP"));
        assertTrue(q.sql().contains("ORDER BY created_at DESC NULLS LAST, request_id DESC"));
    }

    @Test
    void searchTextIsBoundAsParameterWithEscapedWildcards() {
        RequestSearchCriteria c = new RequestSearchCriteria();
        c.setText("50%_off' OR 1=1");
        SqlQuery q = RequestQueryBuilder.count(c);
        assertFalse(q.sql().contains("OR 1=1"));
        assertEquals(6, q.params().size());
        assertEquals("%50\\%\\_off' OR 1=1%", q.params().get(0));
    }

    @Test
    void placeholderCountAlwaysMatchesParameterCount() {
        RequestSearchCriteria c = RequestSearchCriteria.forReport(ReportType.OPEN);
        c.setText("pothole");
        c.getStatusCodes().add("ASSIGNED");
        c.getStatusCodes().add("IN_PROGRESS");
        c.getCategoryIds().add(1);
        c.getPriorities().add("high");
        c.getPriorities().add("not-a-priority");      // ignored
        c.setAssigneeId(7L);
        c.setCreatedFrom(LocalDate.of(2026, 1, 1));
        c.setCreatedTo(LocalDate.of(2026, 3, 31));
        for (SqlQuery q : List.of(RequestQueryBuilder.count(c), RequestQueryBuilder.page(c), RequestQueryBuilder.export(c))) {
            long placeholders = q.sql().chars().filter(ch -> ch == '?').count();
            assertEquals(placeholders, q.params().size(), q.sql());
        }
    }

    @Test
    void createdToIsInclusiveOfTheWholeDay() {
        RequestSearchCriteria c = new RequestSearchCriteria();
        c.setCreatedTo(LocalDate.of(2026, 3, 31));
        List<Object> params = new ArrayList<>();
        String where = RequestQueryBuilder.where(c, params);
        assertTrue(where.contains("created_at < ?"));
        assertEquals(LocalDate.of(2026, 4, 1), params.get(0));
    }

    @Test
    void pagingUsesLimitAndOffset() {
        RequestSearchCriteria c = new RequestSearchCriteria();
        c.setPageSize(10);
        c.setPage(3);
        SqlQuery q = RequestQueryBuilder.page(c);
        List<Object> p = q.params();
        assertEquals(10, p.get(p.size() - 2));
        assertEquals(20, p.get(p.size() - 1));
    }

    @Test
    void pageSizeIsCapped() {
        RequestSearchCriteria c = new RequestSearchCriteria();
        c.setPageSize(1_000_000);
        assertEquals(RequestSearchCriteria.MAX_PAGE_SIZE, c.getPageSize());
    }
}
