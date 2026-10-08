# Security and performance considerations (proposed)

No authentication, authorization, API, or persistence implementation exists yet. This document sets requirements for the first protected API slice without choosing a token framework prematurely.

## Security model

- Authentication: authenticate customer and operations identities through a selected identity provider before exposing APIs. Provider, session/token format, rotation, and revocation remain undecided. Future tokens must have short, appropriate validity, verified issuer/audience/signature, and no sensitive shipment data embedded in claims.
- Authorization: roles are starting points, not the whole policy. Customer users may access only resources belonging to their authenticated customer account. Dispatchers may assign/release resources within their operational remit. Drivers may submit/read only their assigned work and location if that workflow is approved. Administrative access is separately granted and audited.
- Resource scope: derive customer ownership from verified identity and enforce it in every read and write query/use case. Never accept a body `customerId` as authority. Return a non-disclosing not-found response for cross-customer IDs where existence would leak information.
- IDOR: opaque IDs reduce guessing but are not access control. Test that changing a path ID cannot read, cancel, assign, complete, or inspect another tenant's shipment.
- Privilege escalation: reject client-provided roles, status, assignment, owner, and completion authority fields unless a command explicitly allows them. Validate claims server-side; prevent mass assignment by mapping request contracts deliberately.
- Sensitive data: location history and any driver personal information are sensitive. Minimize collection, restrict read scopes, define retention/deletion and audit policy before storing them. Do not include credentials, tokens, exact location, or personal data in routine logs/events.
- Future token handling: keep signing keys/secrets out of source control, support rotation, and use TLS. Avoid custom token cryptography. Decide CSRF/CORS policy according to browser and token transport rather than enabling broad origins by default.

## Performance and complexity

| Operation | Access pattern and likely cost | Index/pagination and risks |
|---|---|---|
| Create or transition one shipment | Point lookup/update; O(1) application work, database indexed by PK. | PK and customer FK; lock/version contention only on same shipment. No collection loading. |
| Customer shipment list | Page scan by tenant and stable creation order; approximately O(log N + page size) with matching B-tree. | Composite `(customer_id, created_at DESC, shipment_id)`; keyset cursor, bounded page. Avoid ORM lazy-loading customer/dispatch per row. |
| Dispatch availability and assignment | Point locks on shipment, vehicle, driver and one dispatch write; O(1) domain checks. Lock wait under contention is the limiting factor. | PK lookups and partial unique active indexes. Stable lock order; do not load all vehicles/drivers then filter in memory. Hot resource contention is real and should return conflict promptly. |
| Resource assignment history | Range by vehicle/driver, ordered newest first; O(log N + page size). | Add `(resource_id, started_at DESC)` only if history endpoint is confirmed; paginate. |
| Tracking ingest | Append per observation; one conditional projection update. High-frequency write path; O(log N) index work. | Shipment/time index for history and source dedup index only if source provides IDs. Avoid indexing every telemetry attribute. Batch ingestion only if provider throughput requires it and transaction semantics remain clear. |
| Tracking history/latest read | Latest projection is PK O(log N); history is range scan plus page. | Keyset on timestamp plus deterministic sequence/ID. Keep location projection rebuildable. Geospatial/radius queries need a confirmed use case before spatial indexing. |

Expected traffic, latency objectives, retention, and device update frequency are unknown. This analysis is complexity-level guidance, not a capacity claim. Measure query plans and contention before adding caches, partitions, queues, or specialized storage. Use explicit fetch projections to avoid N+1 queries; avoid returning unbounded dispatch or tracking collections.
