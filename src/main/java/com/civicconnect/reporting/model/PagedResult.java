package com.civicconnect.reporting.model;

import java.util.List;

public record PagedResult<T>(List<T> items, long totalCount, int page, int pageSize) {
    public int totalPages() { return (int) Math.max(1, (totalCount + pageSize - 1) / pageSize); }
    public boolean hasPrevious() { return page > 1; }
    public boolean hasNext() { return page < totalPages(); }
    public long firstRow() { return totalCount == 0 ? 0 : (long) (page - 1) * pageSize + 1; }
    public long lastRow() { return (long) (page - 1) * pageSize + items.size(); }
}
