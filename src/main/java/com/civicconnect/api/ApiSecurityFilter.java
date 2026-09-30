package com.civicconnect.api;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Map;
import java.util.Set;

/**
 * Cross-cutting protection for every /api/* call.
 * <ul>
 *   <li><b>CSRF:</b> state-changing calls (POST/PUT/PATCH/DELETE) must carry the custom header
 *       {@value #CSRF_HEADER}: {@value #CSRF_VALUE}. A browser cannot add a custom header to a
 *       cross-site request without a CORS pre-flight, which this API does not grant to other
 *       origins (OWASP CSRF Prevention Cheat Sheet: "custom request headers"). Session cookies are
 *       also HttpOnly and should be SameSite=Lax/Strict (see context.xml note in the push guide).</li>
 *   <li><b>JSON only:</b> bodies must be application/json (blocks form-encoded cross-site posts).</li>
 *   <li><b>No caching</b> of personal data; <b>nosniff</b>.</li>
 *   <li><b>CORS (optional):</b> off by default because the React dev server proxies /api. If the
 *       context parameter {@value #ALLOWED_ORIGIN_PARAM} is set, exactly that origin is allowed.</li>
 * </ul>
 */
@WebFilter(urlPatterns = "/api/*")
public class ApiSecurityFilter extends HttpFilter {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    public static final String CSRF_HEADER = "X-Requested-With";
    public static final String CSRF_VALUE = "CivicConnect";
    public static final String ALLOWED_ORIGIN_PARAM = "civicconnect.allowedOrigin";
    private static final Set<String> UNSAFE = Set.of("POST", "PUT", "PATCH", "DELETE");

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        res.setHeader("Cache-Control", "no-store");
        res.setHeader("X-Content-Type-Options", "nosniff");

        String allowedOrigin = getServletContext().getInitParameter(ALLOWED_ORIGIN_PARAM);
        String origin = req.getHeader("Origin");
        boolean corsAllowed = allowedOrigin != null && !allowedOrigin.isBlank() && allowedOrigin.equals(origin);
        if (corsAllowed) {
            res.setHeader("Access-Control-Allow-Origin", origin);
            res.setHeader("Access-Control-Allow-Credentials", "true");
            res.setHeader("Vary", "Origin");
        }
        if ("OPTIONS".equals(req.getMethod())) {
            if (corsAllowed) {
                res.setHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
                res.setHeader("Access-Control-Allow-Headers", "Content-Type, " + CSRF_HEADER);
                res.setHeader("Access-Control-Max-Age", "600");
                res.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                res.setStatus(HttpServletResponse.SC_FORBIDDEN);
            }
            return;
        }

        if (UNSAFE.contains(req.getMethod())) {
            if (!CSRF_VALUE.equals(req.getHeader(CSRF_HEADER))) {
                reject(res, 403, "CSRF_CHECK_FAILED", "Missing " + CSRF_HEADER + " header.");
                return;
            }
            String type = req.getContentType();
            boolean hasBody = req.getContentLengthLong() > 0 || req.getHeader("Transfer-Encoding") != null;
            if (hasBody && (type == null || !type.toLowerCase().startsWith("application/json"))) {
                reject(res, 415, "UNSUPPORTED_MEDIA_TYPE", "Send the request body as application/json.");
                return;
            }
        }
        chain.doFilter(req, res);
    }

    private static void reject(HttpServletResponse res, int status, String code, String message) throws IOException {
        ApiServlet.writeJson(res, status, Map.of("error", code, "message", message));
    }
}
