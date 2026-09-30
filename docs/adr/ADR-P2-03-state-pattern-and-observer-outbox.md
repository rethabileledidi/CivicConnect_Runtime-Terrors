# ADR-P2-03: State pattern for the lifecycle; Observer + outbox for notifications and simulated SMS/WhatsApp

- **Status:** Proposed for M2 baseline
- **Owner:** Person 2
- **Research evidence:** A2 Task 1 (P1 lifecycle: State vs Strategy vs transition table; P2 reactions: Observer, including the silent-subscriber-failure risk); A2 Task 2 (atomic status + audit); lecturer M1 feedback (SMS/WhatsApp may be simulated)

## Design problem 1: where do the lifecycle rules live?
**Decision:** use the State pattern (`com.civicconnect.lifecycle`). There is one class per status. Each class lists its legal moves and who may make them, and states each move's obligation: an assignee for ASSIGNED, a note for REJECTED and REOPENED, resolution notes for RESOLVED. `RequestLifecycle.plan()` returns a validated `StatusChange`, which Rethabile's repository persists.

**Why not a transition table?** A table can say *whether* SUBMITTED→ASSIGNED is allowed, but not *who* may do it or *what it needs*. **Why not Strategy?** Strategy picks one of several algorithms. Our problem is which behaviour applies at each lifecycle stage.

**Cost:** seven small classes. **Mitigation:** the move list lives in one file (`RequestStates`), and `RequestLifecycleTest` checks all 42 from→to pairs.

## Design problem 2: how do notifications react to a change without endangering it?
**Decision:** Observer. After the repository commits, `StatusChangePublisher` calls `InAppNotificationListener` and `SimulatedMessagingListener`. The messaging listener only **queues** an SMS and a WhatsApp row in `outbox_message`. `OutboxDispatcher` delivers them every 5 seconds through `MessageChannel.Simulated`, with up to 3 attempts, then marks them FAILED.

**Where M2 deliberately differs from A2:**
- A2 listed "write the audit record" and "update reporting" as Observer reactions. The audit row is written **inside** the status-change transaction (ADR-P3-02), and reports read live views (ADR-P3-03). Neither can fail silently.
- A2 suggested an asynchronous handler or message broker for real providers. Simulation removes the external network, so an in-process outbox table is proportionate. A real provider later is one new `MessageChannel` class.

## Consequences
- (+) An illegal move never reaches the database (unit-tested). A failing listener never undoes a change (unit-tested), and failures are logged and returned as warnings.
- (−) With several Tomcat instances, two dispatchers could pick up the same message. Accepted for one server. The fix is `SELECT … FOR UPDATE SKIP LOCKED` when scaling.
- (−) Notifications are written in their own short transactions after the commit. If the server crashes between the commit and the listener, that notification is lost, but the audit history is intact.
