package com.civicconnect.reporting;

import com.civicconnect.data.TransactionRunner;
import com.civicconnect.data.model.ServiceRequestRecord;
import com.civicconnect.reporting.model.CategoryStatusMatrix;
import com.civicconnect.reporting.model.DashboardSnapshot;
import com.civicconnect.reporting.model.LookupOption;
import com.civicconnect.reporting.model.PagedResult;
import com.civicconnect.reporting.model.StatusCount;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.io.Writer;
import java.util.List;

/**
 * Management reporting use cases. Each public method runs in ONE read-only
 * REPEATABLE READ transaction, so every figure it returns comes from the same snapshot.
 */
public class ReportService {

    /** Filter drop-down contents for the report screens. */
    public record FilterOptions(List<LookupOption> statuses, List<LookupOption> categories,
                                List<LookupOption> assignees) { }

    private final TransactionRunner tx;
    private final ReportRepository repository;

    public ReportService(TransactionRunner tx, ReportRepository repository) {
        this.tx = tx;
        this.repository = repository;
    }

    public DashboardSnapshot dashboard() {
        return tx.readOnlySnapshot(c -> {
            List<StatusCount> statuses = repository.statusCounts(c);
            CategoryStatusMatrix matrix = repository.categoryStatusMatrix(c, statuses);
            return new DashboardSnapshot(repository.kpis(c), statuses, repository.categorySummaries(c),
                    matrix, repository.openAgeing(c), repository.monthlyTrend(c));
        });
    }

    /** Count and page come from the same snapshot, so "showing x of N" is always accurate. */
    public PagedResult<ServiceRequestRecord> search(RequestSearchCriteria criteria) {
        return tx.readOnlySnapshot(c -> {
            long total = repository.countRequests(c, criteria);
            List<ServiceRequestRecord> rows = repository.findRequests(c, RequestQueryBuilder.page(criteria));
            return new PagedResult<>(rows, total, criteria.getPage(), criteria.getPageSize());
        });
    }

    /** Writes the full filtered/sorted report (up to MAX_EXPORT_ROWS) as CSV. */
    public int exportCsv(RequestSearchCriteria criteria, Writer out) {
        List<ServiceRequestRecord> rows = tx.readOnlySnapshot(
                c -> repository.findRequests(c, RequestQueryBuilder.export(criteria)));
        try {
            CsvWriter.writeRequests(rows, out);
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
        return rows.size();
    }

    public FilterOptions filterOptions() {
        return tx.readOnlySnapshot(c -> new FilterOptions(
                repository.statusOptions(c), repository.categoryOptions(c), repository.assigneeOptions(c)));
    }
}
