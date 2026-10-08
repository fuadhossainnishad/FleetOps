# ADR-0002: Aggregate boundaries and dispatch consistency

- Status: Accepted as initial direction
- Date: 2026-10-08

## Context

Dispatch assigns one shipment, vehicle, and driver. Concurrent dispatchers can both observe a resource as free, and shipment cancellation can race assignment. Independent commits would permit double booking or inconsistent shipment state.

## Decision

Keep Shipment, Vehicle, Driver, and Dispatch as small roots. Assignment/release is an explicit cross-root PostgreSQL transaction that locks rows in a stable order, checks state, writes all changes, and is backed by unique constraints for one active dispatch per shipment/vehicle/driver. Conflicts roll back wholly and surface as a conflict. Do not hold an aggregate spanning the whole logistics system.

## Consequences

The dispatch use case is intentionally the narrow cross-root transaction. Lock order, constraints, and conflict tests are mandatory implementation details. If dispatch later spans multiple databases/processes, reassess consistency and workflow semantics instead of silently weakening the guarantee.
