package com.civicconnect.reporting;

import com.civicconnect.reporting.model.*;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class DashboardSnapshotTest {

    private static DashboardKpis kpis(long total, long open, long overdue, long resolved, long closed) {
        return new DashboardKpis(total, open, overdue, resolved, closed, 0, 0, 0, 0, null, null, OffsetDateTime.now());
    }

    private static DashboardSnapshot snapshot(DashboardKpis k, long statusTotal) {
        List<StatusCount> statuses = List.of(
                new StatusCount("SUBMITTED", "Submitted", "OPEN", k.openRequests(), k.overdueRequests()),
                new StatusCount("RESOLVED", "Resolved", "RESOLVED", k.resolvedRequests(), 0),
                new StatusCount("CLOSED", "Closed", "CLOSED", statusTotal - k.openRequests() - k.resolvedRequests(), 0));
        List<CategorySummary> cats = List.of(new CategorySummary(1, "C", "Cat", "Dept", 24, k.totalRequests(),
                k.openRequests(), k.overdueRequests(), k.resolvedRequests(), k.closedRequests(), null, null));
        CategoryStatusMatrix matrix = new CategoryStatusMatrix(
                List.of(new CategoryStatusMatrix.Row(1, "Cat", Map.of(), k.totalRequests())), statuses, 5);
        List<AgeingBucket> ageing = List.of(new AgeingBucket(1, "0-2 days", k.openRequests(), k.overdueRequests()));
        return new DashboardSnapshot(k, statuses, cats, matrix, ageing, List.of());
    }

    @Test
    void consistentFiguresReconcile() {
        DashboardSnapshot s = snapshot(kpis(10, 4, 1, 2, 4), 10);
        assertTrue(s.reconciled(), s.reconciliationIssues().toString());
    }

    @Test
    void mismatchedStatusTotalIsReported() {
        DashboardSnapshot s = snapshot(kpis(10, 4, 1, 2, 4), 11);
        assertFalse(s.reconciled());
        assertTrue(s.reconciliationIssues().get(0).startsWith("Status totals"));
    }

    @Test
    void barPercentagesAreSafeForZero() {
        DashboardSnapshot s = snapshot(kpis(0, 0, 0, 0, 0), 0);
        assertEquals(0, s.pct(5, 0));
        assertEquals(50, s.pct(5, 10));
        assertEquals(0, s.heatStep(0));
        assertEquals(6, s.heatStep(5));
        assertEquals(2, s.heatStep(1));
    }
}
