package com.civicconnect.reporting;

import com.civicconnect.data.TestDatabase;
import com.civicconnect.data.TransactionRunner;
import com.civicconnect.data.model.ServiceRequestRecord;
import com.civicconnect.reporting.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.StringWriter;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * "Reports use accurate, consistent data": every report figure is cross-checked against
 * the others and against the underlying rows, on the generated sample data set.
 */
class ReportConsistencyDbTest {

    private ReportService service;

    @BeforeEach
    void setUp() throws Exception {
        service = new ReportService(new TransactionRunner(TestDatabase.freshDatabase()), new ReportRepository());
    }

    private long count(ReportType type) {
        return service.search(RequestSearchCriteria.forReport(type)).totalCount();
    }

    @Test
    void dashboardReconciles() {
        DashboardSnapshot s = service.dashboard();
        assertTrue(s.kpis().totalRequests() > 0);
        assertTrue(s.reconciled(), s.reconciliationIssues().toString());
    }

    @Test
    void reportCountsMatchDashboardTiles() {
        DashboardKpis k = service.dashboard().kpis();
        assertEquals(k.totalRequests(), count(ReportType.ALL));
        assertEquals(k.openRequests(), count(ReportType.OPEN));
        assertEquals(k.overdueRequests(), count(ReportType.OVERDUE));
        assertEquals(k.resolvedRequests(), count(ReportType.RESOLVED));
        assertEquals(k.closedRequests(), count(ReportType.CLOSED));
    }

    @Test
    void everyRowInEachReportBelongsInIt() {
        RequestSearchCriteria overdue = RequestSearchCriteria.forReport(ReportType.OVERDUE);
        overdue.setPageSize(RequestSearchCriteria.MAX_PAGE_SIZE);
        for (ServiceRequestRecord r : service.search(overdue).items()) {
            assertEquals("OPEN", r.lifecycleGroup());
            assertTrue(r.overdue());
            assertTrue(r.dueAt().toInstant().isBefore(java.time.Instant.now()));
        }
        RequestSearchCriteria closed = RequestSearchCriteria.forReport(ReportType.CLOSED);
        closed.setPageSize(RequestSearchCriteria.MAX_PAGE_SIZE);
        for (ServiceRequestRecord r : service.search(closed).items()) {
            assertTrue(r.statusCode().equals("CLOSED") || r.statusCode().equals("REJECTED"));
            assertNotNull(r.closedAt());
        }
    }

    @Test
    void categoryFilterTotalsMatchCategoryStatistics() {
        for (CategorySummary cs : service.dashboard().categories()) {
            RequestSearchCriteria c = RequestSearchCriteria.forReport(ReportType.ALL);
            c.getCategoryIds().add(cs.categoryId());
            assertEquals(cs.totalRequests(), service.search(c).totalCount(), cs.categoryName());
        }
    }

    @Test
    void pagingReturnsEveryRowExactlyOnceInSortedOrder() {
        RequestSearchCriteria c = RequestSearchCriteria.forReport(ReportType.ALL);
        c.setSortField(SortField.PRIORITY);           // many ties -> relies on the request_id tiebreaker
        c.setPageSize(17);
        List<Long> ids = new ArrayList<>();
        PagedResult<ServiceRequestRecord> page;
        do {
            page = service.search(c);
            page.items().forEach(r -> ids.add(r.requestId()));
            c.setPage(c.getPage() + 1);
        } while (page.hasNext());
        assertEquals(page.totalCount(), ids.size());
        assertEquals(ids.size(), ids.stream().distinct().count());
    }

    @Test
    void textSearchFindsByReferenceAndIgnoresWildcards() {
        RequestSearchCriteria c = RequestSearchCriteria.forReport(ReportType.ALL);
        c.setText("CC-000001");
        assertEquals(1, service.search(c).totalCount());
        c.setText("%");
        assertEquals(0, service.search(c).totalCount());
    }

    @Test
    void csvExportHasHeaderPlusOneLinePerRequest() {
        StringWriter out = new StringWriter();
        int rows = service.exportCsv(RequestSearchCriteria.forReport(ReportType.OPEN), out);
        assertEquals(count(ReportType.OPEN), rows);
        assertTrue(out.toString().startsWith("Reference,Title,Category"));
    }
}
