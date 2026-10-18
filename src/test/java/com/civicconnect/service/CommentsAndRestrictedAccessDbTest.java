package com.civicconnect.service;

import com.civicconnect.data.JdbcServiceRequestRepository;
import com.civicconnect.data.TestDatabase;
import com.civicconnect.data.TransactionRunner;
import com.civicconnect.data.model.CreatedRequest;
import com.civicconnect.data.model.NewServiceRequest;
import com.civicconnect.data.model.ServiceRequestRecord;
import com.civicconnect.reporting.ReportRepository;
import com.civicconnect.reporting.ReportService;
import com.civicconnect.reporting.RequestSearchCriteria;
import com.civicconnect.reporting.ReportType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

/**
 * TC-26: the V7 schema rules for FR-09 and FR-12, checked against real PostgreSQL 16
 * (the database backstop behind the service-layer rules in TC-24 and TC-25).
 */
class CommentsAndRestrictedAccessDbTest {

    private DataSource ds;
    private JdbcRequestQueries queries;
    private JdbcServiceRequestRepository repo;
    private ReportService reports;
    private int safetyCategory;
    private final long staff = 6;
    private final long requester = 20;

    @BeforeEach
    void setUp() throws Exception {
        ds = TestDatabase.freshDatabase();
        TransactionRunner tx = new TransactionRunner(ds);
        queries = new JdbcRequestQueries(tx);
        repo = new JdbcServiceRequestRepository(tx);
        reports = new ReportService(tx, new ReportRepository());
        safetyCategory = queries.listActiveCategories().stream()
                .filter(c -> c.code().equals("SAFETY")).findFirst().orElseThrow().categoryId();
    }

    private CreatedRequest submit(int categoryId) {
        return repo.create(new NewServiceRequest("Broken fence at substation", "The fence around the substation is down.",
                categoryId, "HIGH", requester, "1 Pretorius St"));
    }

    // ------------------------------------------------------------------ FR-09

    @Test
    void commentsAreStoredWithAuthorAndDatabaseTimeInOrder() {
        long id = submit(1).requestId();
        queries.addComment(id, staff, "First visit: area cordoned off");
        queries.addComment(id, staff, "Contractor booked for Monday");
        List<RequestQueries.Comment> list = queries.findComments(id);
        assertEquals(List.of("First visit: area cordoned off", "Contractor booked for Monday"),
                list.stream().map(RequestQueries.Comment::body).toList());
        assertEquals("STAFF", list.get(0).authorRole());
        assertFalse(list.get(1).createdAt().isBefore(list.get(0).createdAt()));
    }

    @Test
    void commentsCannotBeEditedOrDeleted() throws Exception {
        long id = submit(1).requestId();
        queries.addComment(id, staff, "Original text");
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            assertThrows(SQLException.class, () -> st.executeUpdate(
                    "UPDATE civic.request_comment SET body = 'tampered', created_at = now() - interval '1 day'"));
            assertThrows(SQLException.class, () -> st.executeUpdate("DELETE FROM civic.request_comment"));
        }
        assertEquals("Original text", queries.findComments(id).get(0).body());
    }

    @Test
    void databaseRejectsBlankAndOverlongComments() {
        long id = submit(1).requestId();
        assertThrows(RuntimeException.class, () -> queries.addComment(id, staff, "   "));
        assertThrows(RuntimeException.class, () -> queries.addComment(id, staff, "x".repeat(1001)));
        assertTrue(queries.findComments(id).isEmpty());
    }

    // ------------------------------------------------------------------ FR-12

    @Test
    void onlyTheSafetyCategoryIsRestricted() {
        assertEquals(Set.of(safetyCategory), queries.restrictedCategoryIds());
        assertTrue(queries.listActiveCategories().stream()
                .allMatch(c -> c.restricted() == (c.categoryId() == safetyCategory)));
    }

    @Test
    void queueLeavesOutRestrictedRequestsOnlyWhenAsked() {
        long restricted = submit(safetyCategory).requestId();
        List<Long> withRestricted = queries.listOpenQueue(null, null, true, 10_000).stream()
                .map(ServiceRequestRecord::requestId).toList();
        List<Long> without = queries.listOpenQueue(null, null, false, 10_000).stream()
                .map(ServiceRequestRecord::requestId).toList();
        assertTrue(withRestricted.contains(restricted));
        assertFalse(without.contains(restricted));
        assertEquals(withRestricted.size() - 1, without.size());
    }

    @Test
    void reportsLeaveOutRestrictedRequestsForCoordinators() {
        submit(safetyCategory);
        RequestSearchCriteria all = RequestSearchCriteria.forReport(ReportType.ALL);
        RequestSearchCriteria forCoordinator = RequestSearchCriteria.forReport(ReportType.ALL);
        forCoordinator.setExcludeRestricted(true);
        assertEquals(reports.search(all).totalCount() - 1, reports.search(forCoordinator).totalCount());
        assertTrue(reports.isRestrictedCategory(safetyCategory));
        assertFalse(reports.isRestrictedCategory(1));
    }

    // ------------------------------------------------------------------ TD-02

    @Test
    void databaseCapsDescriptionLikeTheService() throws Exception {
        try (Connection c = ds.getConnection(); Statement st = c.createStatement()) {
            assertThrows(SQLException.class, () -> st.executeUpdate(
                    "UPDATE civic.service_request SET description = repeat('x', 2001) WHERE request_id = 1"));
        }
    }
}
