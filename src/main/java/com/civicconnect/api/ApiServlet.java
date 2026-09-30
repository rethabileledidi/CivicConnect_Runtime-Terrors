package com.civicconnect.api;

import com.civicconnect.auth.AuthenticatedUser;
import com.civicconnect.common.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Base class for the JSON API (Presentation/API layer). It only translates HTTP to service calls
 * and exceptions to status codes; there are no business rules here.
 * <p>
 * Error body: {@code {"error": "CODE", "message": "...", "fieldErrors": {...}}}.
 * Unexpected errors return a generic 500 message; details go to the server log only
 * (OWASP: do not leak stack traces).
 */
public abstract class ApiServlet extends HttpServlet {

    @java.io.Serial
    private static final long serialVersionUID = 1L;
    private static final Logger LOG = Logger.getLogger(ApiServlet.class.getName());

    public static final String SESSION_USER = "civic.user";
    /** Read by Rethabile's ManagementAccessFilter for /dashboard and /reports. */
    public static final String SESSION_ROLE = "userRole";
    public static final String SESSION_USER_ID = "userId";

    @FunctionalInterface
    protected interface Handler {
        Object handle() throws IOException;
    }

    protected BackendServices services() {
        return (BackendServices) getServletContext().getAttribute(BackendServices.ATTRIBUTE);
    }

    /** Runs the handler, writes its result as JSON with {@code successStatus}, maps exceptions. */
    protected void respond(HttpServletResponse res, int successStatus, Handler handler) throws IOException {
        try {
            Object body = handler.handle();
            if (body == null) {
                res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                writeJson(res, successStatus, body);
            }
        } catch (ApiException e) {
            Map<String, Object> error = new LinkedHashMap<>();
            error.put("error", e.code());
            error.put("message", e.getMessage());
            if (!e.fieldErrors().isEmpty()) error.put("fieldErrors", e.fieldErrors());
            writeJson(res, e.httpStatus(), error);
        } catch (JsonProcessingException e) {
            writeJson(res, 400, Map.of("error", "BAD_JSON", "message", "The request body is not valid JSON."));
        } catch (IOException e) {
            writeJson(res, 400, Map.of("error", "BAD_REQUEST", "message", e.getMessage() == null ? "Bad request." : e.getMessage()));
        } catch (RuntimeException e) {
            LOG.log(Level.SEVERE, "Unhandled API error", e);
            writeJson(res, 500, Map.of("error", "SERVER_ERROR", "message", "Something went wrong on our side. Please try again."));
        }
    }

    protected static void writeJson(HttpServletResponse res, int status, Object body) throws IOException {
        res.setStatus(status);
        res.setContentType("application/json");
        res.setCharacterEncoding("UTF-8");
        res.getWriter().write(Json.write(body));
    }

    /** The signed-in user, or null. */
    protected static AuthenticatedUser currentUser(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        return session == null ? null : (AuthenticatedUser) session.getAttribute(SESSION_USER);
    }

    protected static AuthenticatedUser requireUser(HttpServletRequest req) {
        AuthenticatedUser user = currentUser(req);
        if (user == null) throw new ApiException.Unauthenticated("Please sign in.");
        return user;
    }

    /** Parses "/123/status" style path info into segments: ["123", "status"]. */
    protected static String[] segments(HttpServletRequest req) {
        String info = req.getPathInfo();
        if (info == null || info.equals("/")) return new String[0];
        return info.substring(1).split("/");
    }

    protected static long parseId(String raw) {
        try {
            long id = Long.parseLong(raw);
            if (id <= 0) throw new NumberFormatException();
            return id;
        } catch (NumberFormatException e) {
            throw new ApiException.NotFound("Request not found.");
        }
    }

    protected static <T> T body(HttpServletRequest req, Class<T> type) throws IOException {
        return Json.read(req.getInputStream(), type);
    }

    @Override
    protected void service(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        if (services() == null) {
            writeJson(res, 503, Map.of("error", "UNAVAILABLE", "message", "The service is starting up or misconfigured."));
            return;
        }
        super.service(req, res);
    }
}
