package com.civicconnect.reporting.model;

import java.util.ArrayList;
import java.util.List;

/**
 * Everything on the management dashboard, read from ONE database snapshot.
 * {@link #reconciliationIssues()} cross-checks the figures against each other so any
 * inconsistency is visible on the dashboard instead of silently misleading management.
 */
public record DashboardSnapshot(DashboardKpis kpis, List<StatusCount> statusCounts,
                                List<CategorySummary> categories, CategoryStatusMatrix matrix,
                                List<AgeingBucket> ageing, List<MonthlyTrendPoint> trend) {

    public List<String> reconciliationIssues() {
        List<String> issues = new ArrayList<>();
        long total = kpis.totalRequests();

        long byStatus = statusCounts.stream().mapToLong(StatusCount::requestCount).sum();
        if (byStatus != total) issues.add("Status totals (" + byStatus + ") do not equal total requests (" + total + ")");

        long byCategory = categories.stream().mapToLong(CategorySummary::totalRequests).sum();
        if (byCategory != total) issues.add("Category totals (" + byCategory + ") do not equal total requests (" + total + ")");

        long byGroup = kpis.openRequests() + kpis.resolvedRequests() + kpis.closedRequests();
        if (byGroup != total) issues.add("Open + Resolved + Closed (" + byGroup + ") do not equal total requests (" + total + ")");

        long overdueByStatus = statusCounts.stream().mapToLong(StatusCount::overdueCount).sum();
        if (overdueByStatus != kpis.overdueRequests()) issues.add("Overdue by status (" + overdueByStatus + ") does not equal overdue KPI (" + kpis.overdueRequests() + ")");

        long openAgeing = ageing.stream().mapToLong(AgeingBucket::openRequests).sum();
        if (openAgeing != kpis.openRequests()) issues.add("Ageing buckets (" + openAgeing + ") do not equal open requests (" + kpis.openRequests() + ")");

        long matrixTotal = matrix.rows().stream().mapToLong(CategoryStatusMatrix.Row::total).sum();
        if (matrixTotal != total) issues.add("Category x status matrix (" + matrixTotal + ") does not equal total requests (" + total + ")");

        if (kpis.overdueRequests() > kpis.openRequests()) issues.add("Overdue exceeds open requests");
        return issues;
    }

    public boolean reconciled() {
        return reconciliationIssues().isEmpty();
    }

    public long maxStatusCount() {
        return statusCounts.stream().mapToLong(StatusCount::requestCount).max().orElse(0);
    }

    public long maxTrendCount() {
        return trend.stream().mapToLong(t -> Math.max(t.createdCount(), t.resolvedCount())).max().orElse(0);
    }

    public long maxAgeingCount() {
        return ageing.stream().mapToLong(AgeingBucket::openRequests).max().orElse(0);
    }

    /** Bar length as a whole percentage of {@code max} (0 when max is 0) - used by the JSP bars. */
    public int pct(long value, long max) {
        return max <= 0 ? 0 : (int) Math.round(value * 100.0 / max);
    }

    /** Heat-map step 0..6 for the category x status matrix (0 = zero requests). */
    public int heatStep(long value) {
        long max = matrix.maxCellCount();
        if (value <= 0 || max <= 0) return 0;
        return (int) Math.max(1, Math.min(6, Math.ceil(value * 6.0 / max)));
    }
}
