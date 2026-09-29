package com.civicconnect.data;

import com.civicconnect.data.model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/** Verifies the persistence decisions in ADR-P3-02 against a real PostgreSQL database. */
class JdbcServiceRequestRepositoryDbTest {

    private DataSource ds;
    private JdbcServiceRequestRepository repo;
    private final long staff = 6;
    private final long coordinator = 4;

    @BeforeEach
    void setUp() throws Exception {
        ds = TestDatabase.freshDatabase();
        repo = new JdbcServiceRequestRepository(new TransactionRunner(ds));
    }

    private CreatedRequest newRequest() {
        return repo.create(new NewServiceRequest("Pothole outside school", "Deep pothole", 1, "high", 20, "5 Main Rd"));
    }

    @Test
    void createWritesRequestAndInitialHistoryTogether() {
        CreatedRequest created = newRequest();
        assertTrue(created.referenceNo().startsWith("CC-"));
        ServiceRequestRecord r = repo.findById(created.requestId()).orElseThrow();
        assertEquals("SUBMITTED", r.statusCode());
        assertEquals("HIGH", r.priority());
        assertNotNull(r.dueAt(), "due_at must be derived from the category SLA");
        List<StatusHistoryEntry> h = repo.findHistory(created.requestId());
        assertEquals(1, h.size());
        assertNull(h.get(0).fromStatus());
        assertEquals("SUBMITTED", h.get(0).toStatus());
    }

    @Test
    void statusChangeUpdatesRequestAndHistoryAtomically() {
        CreatedRequest c = newRequest();
        int v1 = repo.changeStatus(new StatusChange(c.requestId(), c.version(), RequestStatusCode.SUBMITTED,
                RequestStatusCode.ASSIGNED, coordinator, staff, "Assigned to roads team", null));
        int v2 = repo.changeStatus(new StatusChange(c.requestId(), v1, RequestStatusCode.ASSIGNED,
                RequestStatusCode.IN_PROGRESS, staff, null, null, null));
        repo.changeStatus(new StatusChange(c.requestId(), v2, RequestStatusCode.IN_PROGRESS,
                RequestStatusCode.RESOLVED, staff, null, "Patched", "Pothole filled"));

        ServiceRequestRecord r = repo.findById(c.requestId()).orElseThrow();
        assertEquals("RESOLVED", r.statusCode());
        assertEquals(v2 + 1, r.version());
        assertEquals(staff, r.assigneeId());
        assertNotNull(r.resolvedAt());
        List<StatusHistoryEntry> h = repo.findHistory(c.requestId());
        assertEquals(List.of("SUBMITTED", "ASSIGNED", "IN_PROGRESS", "RESOLVED"),
                h.stream().map(StatusHistoryEntry::toStatus).toList());
    }

    @Test
    void staleVersionIsRejectedAndNothingIsWritten() {
        CreatedRequest c = newRequest();
        repo.changeStatus(new StatusChange(c.requestId(), c.version(), RequestStatusCode.SUBMITTED,
                RequestStatusCode.ASSIGNED, coordinator, staff, null, null));
        // A second user still holding version 0 tries to reject the request
        assertThrows(StaleUpdateException.class, () -> repo.changeStatus(new StatusChange(c.requestId(), c.version(),
                RequestStatusCode.SUBMITTED, RequestStatusCode.REJECTED, coordinator, null, "dup", null)));
        assertEquals("ASSIGNED", repo.findById(c.requestId()).orElseThrow().statusCode());
        assertEquals(2, repo.findHistory(c.requestId()).size());
    }

    @Test
    void databaseRejectsAssignmentWithoutAssigneeAndRollsBack() {
        CreatedRequest c = newRequest();
        assertThrows(DataIntegrityException.class, () -> repo.changeStatus(new StatusChange(c.requestId(), c.version(),
                RequestStatusCode.SUBMITTED, RequestStatusCode.ASSIGNED, coordinator, null, null, null)));
        assertEquals("SUBMITTED", repo.findById(c.requestId()).orElseThrow().statusCode());
        assertEquals(1, repo.findHistory(c.requestId()).size());
    }

    @Test
    void statusChangeWithoutHistoryIsRejectedAtCommit() throws Exception {
        CreatedRequest c = newRequest();
        TransactionRunner tx = new TransactionRunner(ds);
        assertThrows(DataIntegrityException.class, () -> tx.inTransaction(conn -> {
            try (Statement st = conn.createStatement()) {
                return st.executeUpdate("UPDATE civic.service_request SET status_code = 'REJECTED', closed_at = now()"
                        + " WHERE request_id = " + c.requestId());
            }
        }));
        assertEquals("SUBMITTED", repo.findById(c.requestId()).orElseThrow().statusCode());
    }

    @Test
    void historyCannotBeEdited() throws Exception {
        try (Connection conn = ds.getConnection(); Statement st = conn.createStatement()) {
            assertThrows(java.sql.SQLException.class,
                    () -> st.executeUpdate("UPDATE civic.request_status_history SET note = 'tampered' WHERE history_id = 1"));
        }
    }

    @Test
    void unknownRequestGivesNotFound() {
        assertThrows(RecordNotFoundException.class, () -> repo.changeStatus(new StatusChange(999_999, 0,
                RequestStatusCode.SUBMITTED, RequestStatusCode.ASSIGNED, coordinator, staff, null, null)));
    }
}
