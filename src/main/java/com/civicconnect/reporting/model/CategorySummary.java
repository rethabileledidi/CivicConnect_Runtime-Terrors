package com.civicconnect.reporting.model;

import com.civicconnect.util.DisplayFormat;

import java.math.BigDecimal;

public record CategorySummary(int categoryId, String categoryCode, String categoryName, String departmentName,
                              int slaHours, long totalRequests, long openRequests, long overdueRequests,
                              long resolvedRequests, long closedRequests,
                              BigDecimal avgResolutionHours, BigDecimal slaCompliancePct) {
    public String avgResolutionHoursText() { return DisplayFormat.number(avgResolutionHours); }
    public String slaCompliancePctText()   { return DisplayFormat.percent(slaCompliancePct); }
}
