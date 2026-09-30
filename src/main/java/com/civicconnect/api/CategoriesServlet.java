package com.civicconnect.api;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** GET /api/categories - public list of active categories with SLA hours (used by the landing page and form). */
@WebServlet(urlPatterns = "/api/categories")
public class CategoriesServlet extends ApiServlet {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        respond(res, 200, () -> services().requests().categories());
    }
}
