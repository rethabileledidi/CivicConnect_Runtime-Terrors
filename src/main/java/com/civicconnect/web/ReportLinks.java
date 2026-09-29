package com.civicconnect.web;

import com.civicconnect.reporting.RequestSearchCriteria;
import com.civicconnect.reporting.SortField;

/** Builds sort/page/export links for the report JSP while keeping the active filters. */
public class ReportLinks {

    private final String basePath;
    private final RequestSearchCriteria criteria;

    public ReportLinks(String basePath, RequestSearchCriteria criteria) {
        this.basePath = basePath;
        this.criteria = criteria;
    }

    /** Clicking the active column toggles direction; a new column starts descending (ascending for text). */
    public String sort(String field) {
        SortField f = SortField.fromParam(field);
        boolean asc;
        if (f == criteria.getSortField()) {
            asc = !criteria.isAscending();
        } else {
            asc = f == SortField.REFERENCE || f == SortField.TITLE || f == SortField.CATEGORY
                    || f == SortField.ASSIGNEE || f == SortField.STATUS || f == SortField.DUE;
        }
        return basePath + "?" + SearchCriteriaParser.toQueryString(criteria, f, asc, 1);
    }

    /** "asc", "desc" or "" for the column header indicator. */
    public String indicator(String field) {
        if (SortField.fromParam(field) != criteria.getSortField()) return "";
        return criteria.isAscending() ? "asc" : "desc";
    }

    public String page(int page) {
        return basePath + "?" + SearchCriteriaParser.toQueryString(criteria, criteria.getSortField(), criteria.isAscending(), page);
    }

    public String csv() {
        return basePath + "?" + SearchCriteriaParser.toQueryString(criteria, criteria.getSortField(), criteria.isAscending(), 1)
                + "&format=csv";
    }
}
