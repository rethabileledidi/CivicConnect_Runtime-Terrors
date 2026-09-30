package com.civicconnect.api;

import com.civicconnect.auth.AuthService;
import com.civicconnect.auth.AuthenticatedUser;
import com.civicconnect.common.ApiException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;

/**
 * POST /api/auth/register  - create a RESIDENT account and sign in
 * POST /api/auth/login     - sign in
 * POST /api/auth/logout    - sign out
 * GET  /api/auth/me        - who is signed in (200 with user, or 401)
 */
@WebServlet(urlPatterns = "/api/auth/*")
public class AuthServlet extends ApiServlet {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    record LoginBody(String email, String password) { }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        respond(res, 200, () -> {
            if (!"/me".equals(req.getPathInfo())) throw new ApiException.NotFound("Unknown endpoint.");
            return requireUser(req);
        });
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String path = req.getPathInfo() == null ? "" : req.getPathInfo();
        switch (path) {
            case "/register" -> respond(res, 201, () -> {
                AuthenticatedUser user = services().auth().register(body(req, AuthService.Registration.class));
                startSession(req, user);
                return user;
            });
            case "/login" -> respond(res, 200, () -> {
                LoginBody b = body(req, LoginBody.class);
                AuthenticatedUser user = services().auth().login(b.email(), b.password());
                startSession(req, user);
                return user;
            });
            case "/logout" -> respond(res, 204, () -> {
                HttpSession session = req.getSession(false);
                if (session != null) session.invalidate();
                return null;
            });
            default -> respond(res, 404, () -> { throw new ApiException.NotFound("Unknown endpoint."); });
        }
    }

    /**
     * Session fixation defence (OWASP Session Management Cheat Sheet): a new session ID is issued
     * at sign-in. The role is also stored as "userRole" for Rethabile's ManagementAccessFilter.
     */
    private static void startSession(HttpServletRequest req, AuthenticatedUser user) {
        if (req.getSession(false) != null) req.changeSessionId();
        HttpSession session = req.getSession(true);
        session.setAttribute(SESSION_USER, user);
        session.setAttribute(SESSION_ROLE, user.role().name());
        session.setAttribute(SESSION_USER_ID, user.userId());
    }
}
