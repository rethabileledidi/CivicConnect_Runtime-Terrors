package com.civicconnect.reporting.model;

public record StatusCount(String statusCode, String displayName, String lifecycleGroup,
                          long requestCount, long overdueCount) { }
