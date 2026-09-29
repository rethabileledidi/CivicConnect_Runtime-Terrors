package com.civicconnect.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebFilter;
import jakarta.servlet.http.HttpFilter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;

import java.io.IOException;
import java.util.Set;

/**
 * Deny-by-default access control for the management dashboard and reports
 * (A2 Q2 chain 2, OWASP A01). Only MANAGER, COORDINATOR and ADMIN may view oversight data.
 *
 * The login module stores the signed-in user's role in the session attribute
 * {@value #ROLE_ATTRIBUTE}. For local demos only, the context parameter
 * {@value #DEV_ROLE_PARAM} (META-INF/context.xml) can supply a role; leave it empty in
 * any shared or deployed environment.
 */
@WebFilter(urlPatterns = {"/dashboard", "/reports/*"})
public class ManagementAccessFilter extends HttpFilter {

    public static final String ROLE_ATTRIBUTE = "userRole";
    public static final String DEV_ROLE_PARAM = "civicconnect.devAutoLoginRole";
    static final Set<String> ALLOWED_ROLES = Set.of("MANAGER", "COORDINATOR", "ADMIN");

    @Override
    protected void doFilter(HttpServletRequest req, HttpServletResponse res, FilterChain chain)
            throws IOException, ServletException {
        String role = currentRole(req);
        if (role == null || !ALLOWED_ROLES.contains(role)) {
            res.sendError(role == null ? HttpServletResponse.SC_UNAUTHORIZED : HttpServletResponse.SC_FORBIDDEN,
                    "Management reports are restricted to managers, coordinators and administrators.");
            return;
        }
        req.setAttribute("currentRole", role);
        res.setHeader("Cache-Control", "no-store");       // oversight data should not be cached
        res.setHeader("X-Content-Type-Options", "nosniff");
        chain.doFilter(req, res);
    }

    private String currentRole(HttpServletRequest req) {
        HttpSession session = req.getSession(false);
        Object role = session == null ? null : session.getAttribute(ROLE_ATTRIBUTE);
        if (role == null) {
            String devRole = getServletContext().getInitParameter(DEV_ROLE_PARAM);
            if (devRole != null && !devRole.isBlank()) role = devRole.trim();
        }
        return role == null ? null : role.toString().toUpperCase();
    }
}
