package com.civicconnect.web;

import com.civicconnect.data.Database;
import com.civicconnect.data.JdbcServiceRequestRepository;
import com.civicconnect.data.ServiceRequestRepository;
import com.civicconnect.data.TransactionRunner;
import com.civicconnect.reporting.ReportRepository;
import com.civicconnect.reporting.ReportService;
import jakarta.servlet.ServletContext;
import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;

import javax.sql.DataSource;

/**
 * Composition root: wires the DataSource, transaction runner, repositories and services
 * once at start-up and shares them through the ServletContext. Other modules obtain the
 * repository with {@code (ServiceRequestRepository) ctx.getAttribute(REQUEST_REPOSITORY)}.
 */
@WebListener
public class AppContextListener implements ServletContextListener {

    public static final String REPORT_SERVICE = "civic.reportService";
    public static final String REQUEST_REPOSITORY = "civic.serviceRequestRepository";

    @Override
    public void contextInitialized(ServletContextEvent event) {
        ServletContext ctx = event.getServletContext();
        DataSource dataSource = Database.lookup();
        TransactionRunner tx = new TransactionRunner(dataSource);
        ServiceRequestRepository requests = new JdbcServiceRequestRepository(tx);
        ctx.setAttribute(REQUEST_REPOSITORY, requests);
        ctx.setAttribute(REPORT_SERVICE, new ReportService(tx, new ReportRepository()));
        ctx.log("CivicConnect data & reporting module initialised");
    }
}
