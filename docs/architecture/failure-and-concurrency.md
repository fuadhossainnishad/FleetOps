# Failure and concurrency considerations (proposed)

The repository has no persistence or external integrations. This matrix states guarantees an implementation must preserve.

| Failure or race | Required behavior |
|---|---|
| PostgreSQL unavailable | Fail the operation clearly (typically `503`); do not report success or write business state to a cache/broker instead. Bounded connection/statement timeouts; recovery is retry by caller with idempotency where command may have committed ambiguously. |
| Transaction failure | Roll back all changes in the transaction. For dispatch this includes dispatch row, shipment state, and both resource assignments. Do not publish an external event before commit. |
| Concurrent shipment transition | Row lock or version check; one valid transition commits. Stale/illegal command returns conflict and cannot overwrite newer state. |
| Concurrent dispatch assignment | Lock affected rows in stable order and enforce active-assignment uniqueness in PostgreSQL. Losing request gets conflict; rollback leaves no partial resource reservation. |
| Future Redis unavailable | Bypass/disable cache and read PostgreSQL (or fail only cache-dependent optional views with an explicit degraded response). Never block authoritative writes on cache availability. |
| Future Kafka unavailable | Commit domain state and outbox intent together; relay retries with bounded backoff. Do not undo committed business state because downstream broker is down. Alert on outbox age/backlog. |
| Future Elasticsearch unavailable/stale | Continue authoritative writes in PostgreSQL; search projection can lag and must be rebuildable. Do not claim search freshness without a watermark. |
| WebSocket disconnect | No business operation depends on an open socket. Clients reconnect and fetch authorized current state; notifications may be missed/coalesced. |
| Duplicate event | Consumers deduplicate by stable event ID and/or aggregate sequence. Side effect applies once logically; transport may deliver repeatedly. |
| Stale/out-of-order tracking update | Retain valid observation with observed and ingestion timestamps. Update latest projection only if the accepted ordering key is newer; duplicates do not create repeated logical effects. |
| Deadlock/serialization abort | Whole transaction rolls back. Retry only known transient database failures, with a small bounded retry and idempotent command semantics; surface conflict/unavailability after exhaustion. |

## Tracking ordering

GPS observation time is supplied by the source and may be wrong or delayed; ingestion time records when FleetOps accepted it. Preserve both. A source sequence/update ID, if available, is a better deterministic tie-breaker than arbitrary database arrival order. Define an accepted ordering tuple with the device/provider before implementation. Historical rows are authoritative accepted observations. Latest location is a derived convenience projection. Late observations append to history but cannot move the projection backward; a rebuild uses the same deterministic rule. Duplicate detection requires stable source identity; absent that, exact semantic deduplication cannot be promised.
