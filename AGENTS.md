# FleetOps engineering contract

These rules govern future changes. The repository currently contains only the Spring Boot application foundation; architecture documents describe intended decisions, not implemented behavior.

## Baseline

- Use Java 21, Spring Boot, and Maven. Keep the application a modular monolith unless measured operational or organizational constraints justify a split.
- PostgreSQL is the planned authoritative transactional store. Do not add a database, ORM, broker, cache, search engine, security framework, container setup, or other dependency until a concrete vertical slice requires it and its failure behavior is documented.
- Inspect the repository and its current state before proposing files, APIs, models, dependencies, or architecture. Preserve unrelated work and behavior.
- Prefer explicit domain behavior and the smallest design that protects a stated invariant. Do not add entity-per-noun, generic service/repository layers, speculative fields, or abstractions without a demonstrated use.

## Domain and data

- State aggregate roots, invariants, lifecycle rules, transaction boundaries, and concurrency strategy for each write capability.
- Keep aggregates small. Cross-aggregate references use identifiers; do not promise atomic consistency across roots without a specific transaction design.
- Use controlled domain operations for state changes; do not expose unrestricted mutation of lifecycle state.
- Treat persistence models as internal. Define API contracts independently and authorize access to each resource in its customer/operational scope.
- Record important architectural choices as short ADRs. Update docs when decisions change; mark proposed/future behavior clearly.

## Runtime and correctness

- Every concurrent write must identify its serialization/locking/constraint strategy and expected conflict response. Database constraints backstop invariants where applicable.
- Define transaction boundaries and behavior on persistence failure. External integrations must have timeouts, retry/idempotency rules, and a correctness-preserving failure mode before integration.
- PostgreSQL owns durable business state. Caches, projections, search indexes, sockets, and message delivery are derived/distribution mechanisms, never alternate authorities.
- Use pagination for growing collections. Justify indexes by query shape and account for write cost. Analyze important algorithms and avoid N+1 access patterns.
- Protect against IDOR and privilege escalation with server-side resource-scope checks; never trust caller-supplied ownership or role claims without verification.

## Change and verification

- Keep changes focused and commits small and meaningful. Do not create microservices or add technology for résumé keywords.
- Add tests for meaningful domain behavior, conflict cases, and failure handling when implementing code. Do not add tests merely to inflate coverage.
- Run the narrowest relevant verification after code or documentation/configuration changes; do not run infrastructure tests for infrastructure that does not exist.
- Report only behavior that exists. For architecture-only work, clearly say that no runtime feature has been implemented.
