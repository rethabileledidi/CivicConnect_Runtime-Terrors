package com.civicconnect.web;

import com.civicconnect.data.ServiceRequestRepository;
import com.civicconnect.data.model.ServiceRequestRecord;
import jakarta.servlet.ServletException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.util.Optional;

/** GET /reports/request?ref=CC-000123 (or id=123) - request detail with its full audit trail. */
@WebServlet("/reports/request")
public class RequestDetailServlet extends HttpServlet {

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws ServletException, IOException {
        ServiceRequestRepository repo =
                (ServiceRequestRepository) getServletContext().getAttribute(AppContextListener.REQUEST_REPOSITORY);
        Optional<ServiceRequestRecord> found = Optional.empty();
        String ref = req.getParameter("ref");
        String id = req.getParameter("id");
        if (ref != null && ref.matches("(?i)CC-\\d{1,12}")) {
            found = repo.findByReference(ref);
        } else if (id != null && id.matches("\\d{1,12}")) {
            found = repo.findById(Long.parseLong(id));
        }
        if (found.isEmpty()) {
            res.sendError(HttpServletResponse.SC_NOT_FOUND, "Service request not found");
            return;
        }
        req.setAttribute("sr", found.get());
        req.setAttribute("history", repo.findHistory(found.get().requestId()));
        req.getRequestDispatcher("/WEB-INF/jsp/request-detail.jsp").forward(req, res);
    }
}
