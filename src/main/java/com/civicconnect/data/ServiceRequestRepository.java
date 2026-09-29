package com.civicconnect.data;

import com.civicconnect.data.model.CreatedRequest;
import com.civicconnect.data.model.NewServiceRequest;
import com.civicconnect.data.model.ServiceRequestRecord;
import com.civicconnect.data.model.StatusChange;
import com.civicconnect.data.model.StatusHistoryEntry;

import java.util.List;
import java.util.Optional;

/**
 * Persistence port for service requests (Dependency Inversion: the request-lifecycle
 * module depends on this interface, so its State-pattern rules can be unit-tested with
 * an in-memory fake, without a database).
 */
public interface ServiceRequestRepository {

    /** Inserts the request AND its initial SUBMITTED history row in one transaction. */
    CreatedRequest create(NewServiceRequest request);

    Optional<ServiceRequestRecord> findById(long requestId);

    Optional<ServiceRequestRecord> findByReference(String referenceNo);

    /**
     * Persists an already-validated status change and its history row atomically.
     *
     * @return the request's new version number
     * @throws StaleUpdateException    if the request's version/status changed since it was read
     * @throws RecordNotFoundException if the request does not exist
     * @throws DataIntegrityException  if a database integrity rule rejects the change
     */
    int changeStatus(StatusChange change);

    /** Full audit trail, oldest first. */
    List<StatusHistoryEntry> findHistory(long requestId);
}
