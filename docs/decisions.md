# Architecture Decisions

## ADR-001: Single Maven Module with Worker Profile
- **Date**: 2026-11-20
- **Status**: Accepted
- **Context**: Seven-day build; multi-module adds build complexity.
- **Decision**: Single Maven module with distinct packages (`api`, `worker`, `domain`, `persistence`, `config`) and a `worker` Spring profile. Worker runs as a separate process with `--spring.profiles.active=worker`.
- **Consequences**: Simpler build; must maintain package discipline to avoid coupling.

## ADR-002: Version Matrix
- **Java**: 21 (Temurin)
- **Spring Boot**: 3.5.x
- **PostgreSQL**: 17 (Docker)
- **Redis**: 7.x (Docker)
- **RabbitMQ**: 4.x management (Docker)
- **Kafka**: 4.0.x KRaft (Docker, no ZooKeeper)
- **JJWT**: 0.12.6
- **Node.js**: 22.x LTS
- **Frontend**: React + TypeScript + Vite (latest compatible)