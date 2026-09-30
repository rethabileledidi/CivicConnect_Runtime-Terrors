package com.civicconnect.support;

import com.civicconnect.auth.AuthenticatedUser;
import com.civicconnect.auth.UserAccount;
import com.civicconnect.auth.UserRepository;
import com.civicconnect.auth.UserRole;
import com.civicconnect.data.RecordNotFoundException;
import com.civicconnect.data.ServiceRequestRepository;
import com.civicconnect.data.StaleUpdateException;
import com.civicconnect.data.model.CreatedRequest;
import com.civicconnect.data.model.NewServiceRequest;
import com.civicconnect.data.model.ServiceRequestRecord;
import com.civicconnect.data.model.StatusChange;
import com.civicconnect.data.model.StatusHistoryEntry;
import com.civicconnect.notification.NotificationRepository;
import com.civicconnect.notification.OutboxRepository;
import com.civicconnect.service.RequestQueries;

import java.time.Clock;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * In-memory fakes for every persistence port, so the Business-layer rules are tested
 * without PostgreSQL (the same approach Rethabile's README describes for the lifecycle module).
 */
public final class Fakes {

    private Fakes() { }

    public static final Instant NOW = Instant.parse("2026-09-30T08:00:00Z");

    public static Clock fixedClock() {
        return Clock.fixed(NOW, ZoneOffset.UTC);
    }

    public static AuthenticatedUser user(long id, UserRole role) {
        return new AuthenticatedUser(id, "user" + id + "@example.test", "User " + id, role, "0820000000", "Tshwane");
    }

    // ------------------------------------------------------------------ users

    public static final class Users implements UserRepository {
        public final Map<Long, UserAccount> byId = new LinkedHashMap<>();
        private long nextId = 100;

        public UserAccount add(long id, String email, UserRole role, String hash) {
            UserAccount u = new UserAccount(id, email, "User " + id, "0820000000", "Tshwane", role, null, hash, true, 0, null);
            byId.put(id, u);
            return u;
        }

        @Override public Optional<UserAccount> findByEmail(String email) {
            return byId.values().stream().filter(u -> u.email().equalsIgnoreCase(email)).findFirst();
        }

        @Override public Optional<UserAccount> findById(long userId) { return Optional.ofNullable(byId.get(userId)); }

        @Override
        public UserAccount createResident(String email, String fullName, String phone, String municipality, String hash) {
            if (findByEmail(email).isPresent()) throw new DuplicateEmail();
            long id = nextId++;
            UserAccount u = new UserAccount(id, email, fullName, phone, municipality, UserRole.RESIDENT, null, hash, true, 0, null);
            byId.put(id, u);
            return u;
        }

        @Override
        public void recordFailedLogin(long userId, int count, Instant lockedUntil) {
            UserAccount u = byId.get(userId);
            byId.put(userId, new UserAccount(u.userId(), u.email(), u.fullName(), u.phone(), u.municipality(), u.role(),
                    u.departmentId(), u.passwordHash(), u.active(), count, lockedUntil));
        }

        @Override
        public void recordSuccessfulLogin(long userId, Instant at) {
            recordFailedLogin(userId, 0, null);
        }

        @Override public List<UserAccount> findActiveStaff() {
            return byId.values().stream().filter(u -> u.role() == UserRole.STAFF && u.active()).toList();
        }
    }

    // ------------------------------------------------------------------ requests (Rethabile's port)

    public static final class Requests implements ServiceRequestRepository {
        public final Map<Long, ServiceRequestRecord> rows = new LinkedHashMap<>();
        public final Map<Long, List<StatusHistoryEntry>> history = new LinkedHashMap<>();
        public int changeCalls;
        private long nextId = 1;

        @Override
        public CreatedRequest create(NewServiceRequest r) {
            long id = nextId++;
            String ref = String.format("CC-%06d", id);
            rows.put(id, record(id, ref, r.title(), "SUBMITTED", r.requesterId(), null, 0, r.locationText()));
            history.computeIfAbsent(id, k -> new ArrayList<>()).add(entry(null, "SUBMITTED", r.requesterId(), "Request logged"));
            return new CreatedRequest(id, ref, 0);
        }

        @Override public Optional<ServiceRequestRecord> findById(long id) { return Optional.ofNullable(rows.get(id)); }

        @Override public Optional<ServiceRequestRecord> findByReference(String ref) {
            return rows.values().stream().filter(r -> r.referenceNo().equals(ref)).findFirst();
        }

        @Override
        public int changeStatus(StatusChange ch) {
            changeCalls++;
            ServiceRequestRecord r = rows.get(ch.requestId());
            if (r == null) throw new RecordNotFoundException("missing");
            if (r.version() != ch.expectedVersion() || !r.statusCode().equals(ch.fromStatus().name())) {
                throw new StaleUpdateException("stale");
            }
            Long assignee = ch.assigneeId() != null ? ch.assigneeId() : r.assigneeId();
            rows.put(r.requestId(), record(r.requestId(), r.referenceNo(), r.title(), ch.toStatus().name(),
                    r.requesterId(), assignee, r.version() + 1, r.locationText()));
            history.get(r.requestId()).add(entry(ch.fromStatus().name(), ch.toStatus().name(), ch.changedBy(), ch.note()));
            return r.version() + 1;
        }

        @Override public List<StatusHistoryEntry> findHistory(long id) { return history.getOrDefault(id, List.of()); }

        /** Seeds a request directly in a given state (test set-up only). */
        public long seed(String status, long requesterId, Long assigneeId) {
            long id = nextId++;
            rows.put(id, record(id, String.format("CC-%06d", id), "Seeded request", status, requesterId, assigneeId, 3));
            history.put(id, new ArrayList<>(List.of(entry(null, status, requesterId, "seeded"))));
            return id;
        }

        public static ServiceRequestRecord record(long id, String ref, String title, String status,
                                                  long requesterId, Long assigneeId, int version) {
            return record(id, ref, title, status, requesterId, assigneeId, version, "1 Main Rd, Hatfield, Pretoria");
        }

        public static ServiceRequestRecord record(long id, String ref, String title, String status,
                                                  long requesterId, Long assigneeId, int version, String location) {
            OffsetDateTime t = OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC);
            String group = switch (status) {
                case "RESOLVED" -> "RESOLVED";
                case "CLOSED", "REJECTED" -> "CLOSED";
                default -> "OPEN";
            };
            return new ServiceRequestRecord(id, ref, title, "A description long enough", "MEDIUM", location,
                    status, status, group, 3, "Water Leak / Burst Pipe", "Water and Sanitation",
                    requesterId, "Requester", assigneeId, assigneeId == null ? null : "Staff", t, t, t.plusDays(2),
                    null, null, version, false, null, 0, null, null);
        }

        private static StatusHistoryEntry entry(String from, String to, long by, String note) {
            return new StatusHistoryEntry(0, from, from, to, to, by, "User " + by, "ROLE",
                    OffsetDateTime.ofInstant(NOW, ZoneOffset.UTC), null, note);
        }
    }

    // ------------------------------------------------------------------ backend queries

    public static final class Queries implements RequestQueries {
        private final Requests requests;
        public final Map<Long, String> phones = new LinkedHashMap<>();
        public final Map<Long, Feedback> feedback = new LinkedHashMap<>();

        public Queries(Requests requests) { this.requests = requests; }

        @Override public List<ServiceRequestRecord> listByRequester(long requesterId, int limit) {
            return requests.rows.values().stream().filter(r -> r.requesterId() == requesterId).limit(limit).toList();
        }

        @Override public List<ServiceRequestRecord> listOpenQueue(Long assigneeId, String status, int limit) {
            return requests.rows.values().stream()
                    .filter(r -> r.lifecycleGroup().equals("OPEN"))
                    .filter(r -> assigneeId == null || assigneeId.equals(r.assigneeId()))
                    .filter(r -> status == null || status.equals(r.statusCode()))
                    .limit(limit).toList();
        }

        @Override public List<Category> listActiveCategories() {
            return List.of(new Category(3, "WATER_LEAK", "Water Leak / Burst Pipe", "Leaks", "Water and Sanitation", 48));
        }

        @Override public Optional<String> findContactPhone(long id) { return Optional.ofNullable(phones.get(id)); }

        @Override public void saveContactPhone(long id, String phone) { phones.put(id, phone); }

        @Override public Optional<Feedback> findFeedback(long id) { return Optional.ofNullable(feedback.get(id)); }

        @Override public boolean saveFeedback(long id, int rating, String comment, long by) {
            if (feedback.containsKey(id)) return false;
            feedback.put(id, new Feedback(rating, comment, NOW));
            return true;
        }
    }

    // ------------------------------------------------------------------ notifications / outbox

    public static final class Notifications implements NotificationRepository {
        public final List<Notification> items = new ArrayList<>();
        public final List<Long> recipients = new ArrayList<>();

        @Override public void add(long userId, Long requestId, String title, String body) {
            recipients.add(userId);
            items.add(new Notification(items.size() + 1, requestId, title, body, NOW, false));
        }

        @Override public List<Notification> listForUser(long userId, int limit) { return items; }

        @Override public int markAllRead(long userId) { return items.size(); }
    }

    public static final class Outbox implements OutboxRepository {
        public final List<OutboxMessage> messages = new ArrayList<>();

        @Override public void enqueue(long requestId, Channel channel, String recipient, String body) {
            messages.add(new OutboxMessage(messages.size() + 1, requestId, channel, recipient, body,
                    Status.PENDING, 0, NOW, null, null));
        }

        @Override public List<OutboxMessage> findPending(int limit) {
            return messages.stream().filter(m -> m.status() == Status.PENDING).limit(limit).toList();
        }

        @Override public void markSent(long id, Instant at) {
            replace(id, m -> new OutboxMessage(m.messageId(), m.requestId(), m.channel(), m.recipient(), m.body(),
                    Status.SENT, m.attempts() + 1, m.createdAt(), at, m.lastError()));
        }

        @Override public void recordFailure(long id, String error, int max) {
            replace(id, m -> new OutboxMessage(m.messageId(), m.requestId(), m.channel(), m.recipient(), m.body(),
                    m.attempts() + 1 >= max ? Status.FAILED : Status.PENDING, m.attempts() + 1, m.createdAt(), null, error));
        }

        @Override public List<OutboxMessage> findForRequest(long requestId) {
            return messages.stream().filter(m -> m.requestId() == requestId).toList();
        }

        private void replace(long id, java.util.function.UnaryOperator<OutboxMessage> f) {
            for (int i = 0; i < messages.size(); i++) {
                if (messages.get(i).messageId() == id) messages.set(i, f.apply(messages.get(i)));
            }
        }
    }
}
