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

## ADR-003: JWT & Stateless Authentication
- **Date**: 2026-11-20
- **Status**: Accepted
- **Context**: We need secure, scalable authentication without server-side session storage.
- **Decision**: Implemented stateless JWT authentication. Tokens contain minimal claims (email, role, uid) and are signed using HS256. Spring Security is configured with a custom `JwtAuthenticationFilter` to validate tokens on every request.
- **Consequences**: The API is horizontally scalable (any server can validate the token). XSS protection becomes critical on the frontend since tokens are stored client-side.

## ADR-004: Job Submission & Idempotency
- **Date**: 2026-11-20
- **Status**: Accepted
- **Context**: Users might double-click "Submit" or retry requests due to network failures.
- **Decision**: Used `Idempotency-Key` headers and SHA-256 request hashing. If a key is reused with the same payload, the existing job is returned (HTTP 200). If the payload differs, it is rejected (HTTP 409 Conflict). The database enforces this via a partial unique index.