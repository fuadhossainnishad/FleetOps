# ADR-0003: Tracking history and derived latest state

- Status: Accepted as initial direction
- Date: 2026-10-08

## Context

Location updates may be frequent, duplicated, delayed, and out of order. A mutable “current location” alone loses accepted history; treating every update as authoritative latest can move a vehicle backward in time.

## Decision

Model accepted tracking observations as append-only history with observed and ingestion timestamps. Treat latest location as a derived projection updated only by a deterministic newer-order rule. Deduplicate only when stable source identity is available. Keep tracking outside shipment aggregate state.

## Consequences

History and projection can be reconciled/rebuilt, but exact tie-breaking and retention depend on provider and business requirements. Do not add high-volume streaming infrastructure, geospatial extensions, or partitioning before measured need. If tracking volume overwhelms transactional storage, revisit its storage boundary while preserving PostgreSQL authority for core business transactions.
