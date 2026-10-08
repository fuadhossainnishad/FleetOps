# Initial database design (conceptual)

PostgreSQL is the planned authority for transactional business state. No schema, driver, migration tool, ORM, or PostgreSQL dependency currently exists. This design uses relational constraints as the final guard for domain invariants; implementation must choose migration tooling and persistence style when a vertical slice is approved.

## Tables and relationships

| Table (proposed) | Key and ownership | Constraints / purpose |
|---|---|---|
| `customer` | PK `customer_id` | Account identity/status only. Additional customer fields require a confirmed workflow. |
| `shipment` | PK `shipment_id`; FK `customer_id` → customer | Status with CHECK over the selected lifecycle enum; required execution instructions once defined; version or equivalent concurrency token. Index `(customer_id, created_at DESC, shipment_id)` for a customer's newest-first list. |
| `vehicle` | PK `vehicle_id` | Eligibility/current assignment state. Stable operational identifier uniqueness only after identifier type and scope are decided. |
| `driver` | PK `driver_id` | Eligibility/current assignment state. Do not store unnecessary personal data. |
| `dispatch` | PK `dispatch_id`; FKs to shipment, vehicle, driver | Assignment lifecycle and timestamps. At most one active dispatch per shipment, vehicle, and driver via partial unique indexes on each respective ID where active. FK delete behavior should restrict deletion while referenced; preserve history. |
| `tracking_observation` | PK (implementation-defined); FK `shipment_id` | Immutable observation time, ingestion time, location value, and a source update/deduplication key only when source semantics are known. High-cardinality append history. |
| `shipment_latest_location` | PK/FK `shipment_id` | Rebuildable latest-location projection, keyed by shipment. Not authoritative history; update only for a newer accepted observation. Omit if query volume does not justify materialization. |

The latest table is the one explicitly optional relation; it should be introduced only if measured read patterns make deriving latest from history too costly. The conceptual design does not require a separate delivery table, fleet table, event table, or audit table before the corresponding workflow and retention requirements exist.

## Transaction boundaries and constraints

The initial Shipment migration creates only the minimal `customer` identity/active row needed by the Shipment FK and create-time active check, plus the Shipment table. Shipment IDs and Customer IDs use PostgreSQL `UUID`; `created_at` uses `TIMESTAMPTZ`. Pickup and delivery execution instructions are required nonblank `TEXT` values. Hibernate validates the Flyway-owned schema and does not generate it. Shipment updates use a `version` column for optimistic concurrency; stale writes fail rather than overwrite a newer lifecycle state.

- Shipment creation/status transition: one shipment transaction; FK verifies customer existence, and application/domain validation verifies active customer. Use row lock or optimistic version to prevent lost transitions.
- Dispatch assign/release: one PostgreSQL transaction locks shipment, vehicle, and driver in deterministic order, checks eligibility, writes dispatch and all current assignment/status changes. Partial unique constraints are the final at-most-one guard. A conflict rolls back fully.
- Tracking ingest: insert immutable history with a deduplication constraint when source identity is defined; update latest state conditionally in the same transaction. A stale observation is retained historically and does not replace newer latest state.
- Avoid cascaded deletion through operational history. Retention/anonymization policy must be decided before implementing delete behavior.

## Indexes and queries

| Index | Query supported / justification |
|---|---|
| Unique partial `dispatch(shipment_id) WHERE active` | Prevents multiple active assignments for one shipment. |
| Unique partial `dispatch(vehicle_id) WHERE active` | Prevents concurrent active use of one vehicle. |
| Unique partial `dispatch(driver_id) WHERE active` | Prevents concurrent active use of one driver. |
| `shipment(customer_id, created_at DESC, shipment_id)` | Customer-scoped list ordered newest first; composite order enables keyset pagination without scanning other customers. |
| `dispatch(vehicle_id, started_at DESC)` / driver counterpart | Operational assignment history by resource, if that history view is in the initial product. Add only if required. |
| `tracking_observation(shipment_id, observed_at DESC, source_sequence)` | Shipment history and latest lookup. Required if history is exposed; tie-breaker must reflect agreed source semantics. |
| Unique tracking source key, likely `(source_id, source_update_id)` | Deduplicates retransmission only when the GPS provider supplies stable IDs. Do not invent a deduplication key. |

Indexes have write and storage cost; validate query plans using representative data before adding optional history indexes. Shipment/customer lists should use keyset pagination for unbounded growth; cap page size and use an opaque cursor. Operational vehicle/driver availability is a bounded indexed lookup over eligibility/current-assignment state. No geospatial index is justified before radius/route queries are requirements.

## Cardinality and data volume

One Customer has zero or more Shipments; one Shipment has historical Dispatch records and many tracking observations, but at most one active Dispatch and one latest-location projection. Each Vehicle/Driver may have many historical dispatches, but only one active one. Tracking history is likely the highest-cardinality and highest-write table. Partitioning, retention, PostGIS, and separate telemetry storage are deferred until volume, retention, and query targets are known.
