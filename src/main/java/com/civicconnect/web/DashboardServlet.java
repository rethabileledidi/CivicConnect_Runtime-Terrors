package com.civicconnect.web;

import com.civicconnect.reporting.ReportService;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/** GET /dashboard - management and oversight dashboard. */
@WebServlet("/dashboard")
public class DashboardServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        ReportService reports = (ReportService) getServletContext().getAttribute(AppContextListener.REPORT_SERVICE);
        req.setAttribute("snapshot", reports.dashboard());
        req.getRequestDispatcher("/WEB-INF/jsp/dashboard.jsp").forward(req, res);
    }
}
