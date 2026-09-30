package com.civicconnect.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.sql.Connection;
import java.util.Map;

/** GET /api/health - liveness + database check for operational readiness (Master Brief §17). */
@WebServlet(urlPatterns = "/api/health")
public class HealthServlet extends ApiServlet {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        boolean dbUp;
        try (Connection c = services().dataSource().getConnection()) {
            dbUp = c.isValid(2);
        } catch (Exception e) {
            dbUp = false;
        }
        writeJson(res, dbUp ? 200 : 503, Map.of("status", dbUp ? "UP" : "DEGRADED", "database", dbUp ? "UP" : "DOWN"));
    }
}
