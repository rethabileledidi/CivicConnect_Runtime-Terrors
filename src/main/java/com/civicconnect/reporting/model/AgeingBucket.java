package com.civicconnect.reporting.model;

public record AgeingBucket(int order, String label, long openRequests, long overdueRequests) { }
