package com.civicconnect.api;

import com.civicconnect.common.ApiException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Map;

/** GET /api/notifications - my notifications; POST /api/notifications/read - mark all read. */
@WebServlet(urlPatterns = {"/api/notifications", "/api/notifications/*"})
public class NotificationsServlet extends ApiServlet {
    @java.io.Serial
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        respond(res, 200, () -> services().notifications().inbox(requireUser(req)));
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        respond(res, 200, () -> {
            if (!"/read".equals(req.getPathInfo())) throw new ApiException.NotFound("Unknown endpoint.");
            return Map.of("updated", services().notifications().markAllRead(requireUser(req)));
        });
    }
}
