package com.civicconnect.api;

import com.civicconnect.common.ApiException;
import com.civicconnect.service.RequestService;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;

/**
 * GET  /api/requests?scope=mine            - the signed-in user's own requests (default)
 * GET  /api/requests?scope=queue&status=X  - work queue (staff: assigned to me; coordinator/manager: all open)
 * POST /api/requests                       - submit a new request (201)
 * GET  /api/requests/{id}                  - detail: request, history, allowed actions, feedback, simulated messages
 * POST /api/requests/{id}/status           - change status {target, expectedVersion, assigneeId?, note?, resolutionNotes?}
 * POST /api/requests/{id}/feedback         - rate a resolved request {rating 1-5, comment?}
 */
@WebServlet(urlPatterns = {"/api/requests", "/api/requests/*"})
public class RequestsServlet extends ApiServlet {

    @java.io.Serial
    private static final long serialVersionUID = 1L;

    record FeedbackBody(Integer rating, String comment) { }

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String[] seg = segments(req);
        respond(res, 200, () -> {
            RequestService service = services().requests();
            if (seg.length == 0) {
                return "queue".equals(req.getParameter("scope"))
                        ? service.queue(requireUser(req), req.getParameter("status"))
                        : service.listMine(requireUser(req));
            }
            if (seg.length == 1) return service.detail(requireUser(req), parseId(seg[0]));
            throw new ApiException.NotFound("Unknown endpoint.");
        });
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String[] seg = segments(req);
        RequestService service = services().requests();
        if (seg.length == 0) {
            respond(res, 201, () -> service.submit(requireUser(req), body(req, RequestService.SubmitInput.class)));
        } else if (seg.length == 2 && "status".equals(seg[1])) {
            respond(res, 200, () -> service.changeStatus(requireUser(req), parseId(seg[0]),
                    body(req, RequestService.ChangeInput.class)));
        } else if (seg.length == 2 && "feedback".equals(seg[1])) {
            respond(res, 200, () -> {
                FeedbackBody b = body(req, FeedbackBody.class);
                return service.giveFeedback(requireUser(req), parseId(seg[0]), b.rating(), b.comment());
            });
        } else {
            respond(res, 404, () -> { throw new ApiException.NotFound("Unknown endpoint."); });
        }
    }
}
