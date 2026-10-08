# Aggregate boundaries (proposed)

Aggregate roots define transactional consistency boundaries, not a one-to-one mapping from nouns to tables. PostgreSQL is intended to enforce the write invariants. No aggregates are implemented yet.

| Root | Owns / guarantees | References and excluded state |
|---|---|---|
| Customer | Stable customer identity and active/disabled account status. | Shipment IDs are not embedded. Customer lifecycle/deletion policy is unresolved. |
| Shipment | Customer association, shipment lifecycle, and eventual completion outcome. One legal transition at a time. | References Customer ID and current Dispatch ID rather than embedding operational resources or tracking history. |
| Vehicle | Eligibility and at most one current active Dispatch. | Dispatch ID is a reference. No maintenance aggregate or full fleet inventory is assumed. |
| Driver | Eligibility and at most one current active Dispatch. | Dispatch ID is a reference. No HR/licensing model is assumed. |
| Dispatch | Assignment tuple and assignment/release lifecycle. Ensures one shipment assignment at a time and records the chosen resources. | References Shipment, Vehicle, and Driver by ID. No tracking stream is embedded. |

Shipment state is not a copy of dispatch state. An assignment changes both roots in one database transaction so no committed assignment can exist while the shipment remains unassigned (or vice versa). This is a deliberate cross-aggregate transaction, bounded to one relational database. Lock and update order must be consistent (Shipment, Vehicle, Driver, then Dispatch) to reduce deadlocks; retry only recognized transient deadlocks/serialization failures, with bounded attempts.

Tracking observations are immutable records, not an aggregate containing an unbounded collection. The latest-location row/view is derived and can be rebuilt from history. It is updated only when an incoming observation is newer under the chosen ordering rule. No strict synchronization with shipment transitions is promised.

## Dispatch invariants

- Shipment is eligible for assignment and has no active dispatch.
- Vehicle and driver are active/eligible and each has no active dispatch.
- The shipment, vehicle, and driver are updated in one transaction; any failed check or write rolls back the entire assignment.
- Database uniqueness on active assignment references (implementation choice: active row/current-assignment columns or partial unique indexes) backs the at-most-one rules. Exact representation is deferred to schema implementation.
- Concurrent requests that lose a lock/constraint race receive a conflict response and no partial assignment.

Realistic race: two dispatchers concurrently select the same vehicle for different shipments. Both may observe availability before either writes. Locking the vehicle row before checking availability, then updating it and inserting the dispatch in the same transaction, serializes the decision. A database uniqueness constraint remains the final guard if an alternate writer bypasses the lock protocol.

```mermaid
sequenceDiagram
  participant A as Dispatcher A
  participant B as Dispatcher B
  participant DB as PostgreSQL
  A->>DB: Begin; lock shipment, vehicle, driver in order
  B->>DB: Begin; request same vehicle lock (waits)
  A->>DB: Check eligible; insert dispatch; update three roots
  A->>DB: Commit
  DB-->>B: Vehicle lock acquired
  B->>DB: Check vehicle active assignment
  DB-->>B: Conflict; rollback, no partial assignment
```

Vehicle/Driver availability changes follow the same rule: availability cannot be changed to unavailable while actively assigned unless an explicit operational reassignment/release workflow is committed. Whether “inactive” can interrupt a trip needs business policy.
