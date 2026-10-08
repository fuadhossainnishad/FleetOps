# ADR-0001: Modular monolith and PostgreSQL authority

- Status: Accepted as initial direction
- Date: 2026-10-08

## Context

The repository is a single Spring Boot application with no runtime domain, database, or integrations. Initial business workflows need transactional coordination across shipment and operational resources. There is no demonstrated scale or team boundary requiring distributed services.

## Decision

Build capabilities incrementally in a modular monolith. When persistence is introduced, PostgreSQL is the authoritative store for committed business state. Keep modules and dependencies limited to requirements of a concrete vertical slice.

## Consequences

Single-database transactions can protect dispatch consistency without distributed transactions. Module boundaries need discipline because one process does not enforce them automatically. Redis, Kafka, Elasticsearch, WebSocket, and microservices remain optional future decisions backed by evidence. No dependency or runtime implementation is added by this ADR.
