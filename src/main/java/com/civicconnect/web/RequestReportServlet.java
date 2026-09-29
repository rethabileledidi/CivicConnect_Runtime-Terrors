package com.civicconnect.web;

import com.civicconnect.reporting.ReportService;
import com.civicconnect.reporting.RequestSearchCriteria;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.time.LocalDate;

/**
 * GET /reports/requests - open / overdue / resolved / closed / all request reports with
 * search, filtering, sorting and paging. Add format=csv to download the full result.
 */
@WebServlet("/reports/requests")
public class RequestReportServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        ReportService reports = (ReportService) getServletContext().getAttribute(AppContextListener.REPORT_SERVICE);
        RequestSearchCriteria criteria = SearchCriteriaParser.parse(req.getParameterMap());

        if ("csv".equalsIgnoreCase(req.getParameter("format"))) {
            String file = "civicconnect-" + criteria.getReportType().param() + "-requests-" + LocalDate.now() + ".csv";
            res.setContentType("text/csv; charset=UTF-8");
            res.setHeader("Content-Disposition", "attachment; filename=\"" + file + "\"");
            res.getWriter().write('﻿');   // BOM so Excel opens UTF-8 correctly
            reports.exportCsv(criteria, res.getWriter());
            return;
        }

        req.setAttribute("criteria", criteria);
        req.setAttribute("result", reports.search(criteria));
        req.setAttribute("options", reports.filterOptions());
        req.setAttribute("links", new ReportLinks(req.getContextPath() + "/reports/requests", criteria));
        req.getRequestDispatcher("/WEB-INF/jsp/requests.jsp").forward(req, res);
    }
}
