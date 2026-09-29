package com.civicconnect.reporting.model;

import com.civicconnect.util.DisplayFormat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record DashboardKpis(long totalRequests, long openRequests, long overdueRequests,
                            long resolvedRequests, long closedRequests, long rejectedRequests,
                            long unassignedRequests, long createdLast7Days, long resolvedLast7Days,
                            BigDecimal avgResolutionHours, BigDecimal slaCompliancePct,
                            OffsetDateTime generatedAt) {
    public String generatedAtText()        { return DisplayFormat.dateTime(generatedAt); }
    public String avgResolutionHoursText() { return DisplayFormat.number(avgResolutionHours); }
    public String slaCompliancePctText()   { return DisplayFormat.percent(slaCompliancePct); }
    public String avgResolutionDaysText() {
        return avgResolutionHours == null ? "-"
                : DisplayFormat.number(avgResolutionHours.divide(BigDecimal.valueOf(24), 1, java.math.RoundingMode.HALF_UP));
    }
}
