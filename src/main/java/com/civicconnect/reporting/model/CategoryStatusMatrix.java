package com.civicconnect.reporting.model;

import java.util.List;
import java.util.Map;

/**
 * Category x Status cross-tab. Rows and columns come from the lookup tables, so every
 * category and status appears even when its count is zero.
 */
public record CategoryStatusMatrix(List<Row> rows, List<StatusCount> columns, long maxCellCount) {

    public record Row(int categoryId, String categoryName, Map<String, Long> countsByStatus, long total) {
        public long count(String statusCode) {
            return countsByStatus.getOrDefault(statusCode, 0L);
        }
    }
}
