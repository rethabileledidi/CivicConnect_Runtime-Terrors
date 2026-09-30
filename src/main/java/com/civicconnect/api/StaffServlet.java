package com.civicconnect.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** GET /api/staff - active staff members for the coordinator's "assign to" list. */
@WebServlet(urlPatterns = "/api/staff")
public class StaffServlet extends ApiServlet {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        respond(res, 200, () -> services().requests().assignableStaff(requireUser(req)));
    }
}
