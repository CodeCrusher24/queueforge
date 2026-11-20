QUEUEFORGE A Detailed Product Specification, Architecture Guide, and
AI-Assisted Implementation Playbook Version 1.0 | Prepared for a
seven-day intensive build

======================================================================
0. PURPOSE OF THIS DOCUMENT
======================================================================

This document is the source of truth for building QueueForge. Give it to
the AI coding assistant at the start of the project, then ask the
assistant to implement the system in small, testable phases. The
assistant must read this specification before changing code, keep the
architecture consistent, update documentation when decisions change, and
never claim a feature works without running relevant tests.

QueueForge is a developer-facing distributed background-job
orchestration platform. Applications submit jobs through an
authenticated API. QueueForge persists their state, dispatches eligible
jobs to workers, tracks attempts and results, retries eligible failures,
supports delayed and recurring execution, detects abandoned work, and
presents operational data in a dashboard.

The goal is a credible, locally runnable portfolio project with real
engineering depth—not a pretend production platform and not a collection
of technologies added just for their names.

Core stack: - Java 21 (or the current supported LTS installed locally) -
Spring Boot 3.x, using a compatible current release - Maven - Spring
Web - Spring Security - JWT-based access tokens - Spring Data JPA /
Hibernate - PostgreSQL - Flyway database migrations - Redis - RabbitMQ -
Apache Kafka - React + TypeScript + Vite - Docker Compose - JUnit 5,
Mockito, Spring Boot Test, Testcontainers where feasible - OpenAPI /
Swagger UI - Spring Boot Actuator - Optional: Micrometer and
Prometheus-compatible metrics

Versioning rule: before generating the project, choose mutually
compatible stable versions. Do not blindly paste old dependency versions
from tutorials. Record the chosen Java, Spring Boot, PostgreSQL, Redis,
RabbitMQ, Kafka, Node, and frontend versions in README. Avoid changing
versions midway unless there is a clear compatibility reason.

====================================================================== 1.
PRODUCT VISION AND NON-GOALS
======================================================================

1.1 The problem

Many applications need to perform work that should not happen inside a
user-facing HTTP request: send notifications, generate reports, process
webhooks, create invoices, resize images, or run scheduled tasks.
Performing that work synchronously can make requests slow and fragile. A
background-job system separates job submission from job execution and
gives operators a way to inspect progress and failures.

QueueForge will provide: - A secure API to submit and inspect jobs. -
Persistent job state in PostgreSQL. - Asynchronous dispatch using
RabbitMQ. - Event streaming using Kafka for lifecycle events and
analytics. - Redis-backed rate limiting, caching, and worker
presence/heartbeat support. - A worker runtime that executes a small
registry of safe, predefined job handlers. - Retry, backoff,
dead-letter, delayed execution, and recovery behavior. - A dashboard for
operational visibility. - Tests that prove core behavior, including
selected failure cases.

1.2 What QueueForge is not

Do not attempt to recreate all of Temporal, Celery, Sidekiq, Airflow, or
Kubernetes. In seven days, the project is a compact educational
platform. Do not claim production-grade scale, exactly-once execution,
multi-region resilience, or a specific throughput unless measured and
justified.

Do not execute arbitrary source code, shell commands, or user-provided
scripts from submitted jobs. Clients may choose only from registered
handler types and provide validated JSON payloads.

Do not split the project into many microservices merely to appear
sophisticated. Use a modular Spring Boot control-plane application and a
separately runnable worker process. Kafka, RabbitMQ, PostgreSQL, and
Redis are infrastructure dependencies, not reasons to create needless
services.

1.3 Primary users

-   Platform administrator: manages users/tenants, inspects system
    health, and sees operational metrics.
-   Developer/API client: submits jobs and checks status using an API
    key or JWT-backed identity.
-   Operator: filters jobs, inspects attempts, retries failed jobs,
    cancels eligible jobs, and checks worker health.
-   Worker process: claims and executes eligible tasks, then reports
    outcomes.

For the MVP, user accounts and tenant/team management can be simple. Do
not spend the entire build on an elaborate SaaS billing or organization
model.

1.4 Product success criteria

The project is considered complete only when: - A new developer can
start the whole stack from a documented command. - Authentication works
and protected routes reject unauthenticated callers. - A client can
submit a valid job and retrieve its status. - A worker receives and
executes a supported job. - Job state and attempt history survive
application restarts. - Failed jobs retry according to policy and
eventually reach a terminal failed state or dead-letter state. -
Duplicate submissions with the same idempotency key do not create
duplicate logical jobs within the documented scope. - Worker failure and
redelivery do not silently corrupt state. - Kafka receives lifecycle
events from a reliable outbox publisher. - Redis has a documented, real
use case and graceful behavior when unavailable. - The dashboard shows
real API data, not hard-coded mock metrics. - Tests cover the main
lifecycle and at least a few race/failure scenarios. - README explains
architecture, setup, API examples, tradeoffs, limitations, and measured
test results.

======================================================================
2. FEATURE SCOPE: MUST-HAVE, SHOULD-HAVE, STRETCH
======================================================================

MUST-HAVE: 1. Spring Boot API and worker process. 2. PostgreSQL
persistence with Flyway migrations. 3. JWT authentication and role-based
authorization. 4. Job submission, list/detail/status endpoints. 5.
Registered handler types: simulate email, generate report, process
webhook (simulated). 6. Job lifecycle state machine. 7. RabbitMQ
dispatch and acknowledgement. 8. Retry policy and dead-letter handling.
9. Idempotency key support. 10. Job attempts and event/audit history.
11. Redis rate limiting and/or worker heartbeat. 12. Kafka lifecycle
event stream. 13. Transactional outbox. 14. Basic React dashboard. 15.
Docker Compose and a reproducible README. 16. Automated tests for
important flows.

SHOULD-HAVE: - Scheduled/delayed jobs. - Manual retry for terminal
failed jobs. - Cancellation when safe. - Worker registration/heartbeat
view. - Queue metrics and execution duration charts. - Tenant scoping. -
OpenAPI docs. - Actuator health checks. - Integration tests using
Testcontainers.

STRETCH: - Recurring jobs with cron expressions. - Priority
scheduling. - Concurrency limits per queue/tenant. - Server-Sent Events
(SSE) for live updates. - Prometheus metrics and a preconfigured
dashboard. - Job payload/result retention policy. - Rate limiting by
tenant. - Optimistic locking for state transitions. - Load-test script
and report.

Do not start stretch features until the must-have vertical flow works
end to end. A complete core system is more impressive than many
half-built modules.

======================================================================
3. CORE DOMAIN CONCEPTS AND DEFINITIONS
======================================================================

Job: A durable request to perform a predefined kind of work. A job has
an ID, type, payload, status, priority, creation time, scheduled time,
attempt counters, and optional result/error information.

Job type / handler type: A named operation supported by QueueForge, such
as SEND_EMAIL, GENERATE_REPORT, or PROCESS_WEBHOOK. Each handler
validates its payload and performs a bounded operation. The MVP handlers
can simulate external work rather than send real email or call external
services.

Job attempt: One execution attempt for a job. Each attempt records
start/end time, worker ID, outcome, error category, safe error message,
and duration.

Queue: A logical class of jobs. For the MVP, use a default queue and
optionally a high-priority queue. Do not create an arbitrary queue per
user without a clear design.

Worker: A separate process or application profile that consumes
messages, invokes a registered handler, and reports execution outcomes.
Multiple worker instances should be able to run concurrently.

Idempotency key: A client-provided unique token that identifies one
logical submission within a defined scope, typically tenant plus key.
Repeating the same request with the same key should return the existing
job rather than create another one. The server must compare request
payload/hash if the same key is reused for a materially different
request; return a conflict instead of silently changing the existing
job.

Retry: A later attempt after a retryable failure. A retry policy
specifies maximum attempts, delay/backoff, and optionally jitter. Do not
retry permanent validation failures.

Dead-letter: A queue or terminal state for jobs that exhausted retries
or failed permanently. The database remains the canonical record.
RabbitMQ dead-letter configuration is helpful for messages that cannot
be delivered/processed at the broker layer, but application-level job
failure should also be recorded in PostgreSQL.

Outbox event: A database row created in the same transaction as a job
state change. A publisher reads pending rows and publishes them to
Kafka. This reduces the risk of committing database state but losing the
corresponding event when Kafka is unavailable.

Lifecycle event: A domain event such as JobSubmitted, JobStarted,
JobSucceeded, JobRetryScheduled, JobFailed, JobCancelled, or
JobDeadLettered.

At-least-once delivery: A message may be delivered more than once. The
system must be prepared for duplicates. Do not claim exactly-once side
effects. A worker can crash after doing work but before acknowledging
the message, so duplicate execution is possible. Handler idempotency and
state checks reduce damage.

Tenant: A logical customer or workspace. For the first version, tenant
support can be lightweight, but all data-access and authorization
decisions must consistently enforce tenant boundaries if multi-tenancy
is implemented. Never trust a tenant ID supplied in a request without
checking that the authenticated principal is authorized for it.

======================================================================
4. JOB LIFECYCLE AND STATE MACHINE
======================================================================

Use a documented state machine. Recommended states:

-   SCHEDULED: job exists but its scheduled time is in the future.
-   QUEUED: job is eligible and awaiting dispatch/execution.
-   DISPATCHED: job has been published for worker delivery, if this
    state is useful to the implementation.
-   RUNNING: a worker has claimed the job and begun an attempt.
-   RETRY_WAIT: a retryable failure occurred and the next attempt is
    scheduled for later.
-   SUCCEEDED: terminal success.
-   FAILED: terminal failure after permanent failure or exhausted
    retries.
-   CANCELLED: job was cancelled before completion, where cancellation
    is safe.
-   DEAD_LETTERED: optional explicit terminal category for exhausted
    jobs; alternatively use FAILED with a separate dead-letter
    flag/reason. Choose one consistent model.

Do not let controllers update arbitrary status strings. All transitions
must go through a domain service that validates the current state and
allowed next state.

Suggested transitions: - Submission with future runAt -> SCHEDULED. -
Submission due now -> QUEUED. - SCHEDULED -> QUEUED when due. - QUEUED
-> RUNNING when a worker successfully claims it. - RUNNING -> SUCCEEDED
on success. - RUNNING -> RETRY_WAIT on retryable error when attempts
remain. - RETRY_WAIT -> QUEUED when retry time arrives. - RUNNING ->
FAILED when the error is permanent or attempts are exhausted. - FAILED
-> QUEUED only through an explicit manual retry action, which records
the operator and resets/reinitializes attempt policy in a documented
way. - QUEUED or SCHEDULED -> CANCELLED if not yet actively executing. -
RUNNING cancellation should be cooperative and optional; do not claim to
kill arbitrary in-progress work. - SUCCEEDED and CANCELLED are normally
terminal. - Failed or dead-lettered jobs may be manually retried if
policy allows, but the action must preserve historical attempts.

Each transition should: 1. Load the current job state. 2. Check
authorization. 3. Validate the transition. 4. Update the row with
concurrency protection (optimistic version column or conditional
update). 5. Create a job event/audit record. 6. Create an outbox row in
the same database transaction. 7. Commit. 8. Perform asynchronous
publication later through the outbox publisher.

Use a @Version field or a guarded SQL update where appropriate. Do not
assume @Transactional alone prevents races. Transactions define
atomicity boundaries, but concurrent workers can still compete to claim
the same job.

======================================================================
5. DATA MODEL AND DATABASE DESIGN
======================================================================

Use UUID identifiers (Java UUID and PostgreSQL UUID). Use UTC timestamps
stored consistently (e.g., timestamptz). Use Flyway migration scripts as
the source of truth. Do not rely on Hibernate auto-creating production
schema. During development, use ddl-auto=validate or a carefully chosen
local setting; Flyway should create/update schema.

5.1 users Suggested columns: - id UUID primary key - email VARCHAR
unique, normalized to lowercase - password_hash VARCHAR - display_name
VARCHAR - role VARCHAR or role mapping - tenant_id UUID nullable only if
the initial single-tenant design requires it - enabled BOOLEAN -
created_at TIMESTAMPTZ - updated_at TIMESTAMPTZ

Store password hashes with BCrypt or another supported adaptive password
hash. Never store plaintext passwords. Never return password_hash in API
responses.

5.2 tenants (optional but recommended if scoping is implemented) - id
UUID primary key - name VARCHAR - created_at TIMESTAMPTZ - status
VARCHAR

For a seven-day project, one default seeded tenant may be sufficient.
Make tenant boundaries explicit in code and tests if included.

5.3 jobs Suggested columns: - id UUID primary key - tenant_id UUID (if
multi-tenant) - job_type VARCHAR - queue_name VARCHAR - payload JSONB -
status VARCHAR - priority INTEGER - idempotency_key VARCHAR nullable -
request_hash VARCHAR nullable - run_at TIMESTAMPTZ - created_by UUID -
created_at TIMESTAMPTZ - updated_at TIMESTAMPTZ - started_at TIMESTAMPTZ
nullable - finished_at TIMESTAMPTZ nullable - next_attempt_at
TIMESTAMPTZ nullable - attempt_count INTEGER default 0 - max_attempts
INTEGER - last_error_code VARCHAR nullable - last_error_message TEXT
nullable, sanitized - result JSONB nullable - version BIGINT default 0
for optimistic locking - correlation_id UUID or VARCHAR -
cancellation_requested BOOLEAN default false

Indexes: - (tenant_id, created_at DESC) - (tenant_id, status, created_at
DESC) - (status, next_attempt_at) - (status, run_at) - (queue_name,
status, priority DESC, created_at) - unique scoped idempotency
constraint, such as (tenant_id, idempotency_key), where the key is not
null. Use a partial unique index if appropriate for PostgreSQL.

Avoid storing secrets or huge binary data in JSONB payloads. Store
references to object storage for large files if that feature is ever
added. Limit payload size at API boundaries.

5.4 job_attempts - id UUID primary key - job_id UUID foreign key -
attempt_number INTEGER - worker_id VARCHAR - status VARCHAR (STARTED,
SUCCEEDED, FAILED, ABANDONED) - started_at TIMESTAMPTZ - finished_at
TIMESTAMPTZ nullable - duration_ms BIGINT nullable - error_code VARCHAR
nullable - error_message TEXT nullable, sanitized - created_at
TIMESTAMPTZ - unique(job_id, attempt_number)

Attempts are historical records; do not overwrite earlier attempts to
make a retry look like a single execution.

5.5 job_events - id UUID primary key - job_id UUID foreign key -
event_type VARCHAR - from_status VARCHAR nullable - to_status VARCHAR
nullable - actor_type VARCHAR (USER, SYSTEM, WORKER) - actor_id VARCHAR
nullable - details JSONB nullable - created_at TIMESTAMPTZ -
correlation_id VARCHAR nullable

This is the human-readable audit/activity timeline. Keep details safe:
do not store access tokens, passwords, secrets, or entire sensitive
payloads.

5.6 outbox_events - id UUID primary key - aggregate_type VARCHAR (e.g.,
JOB) - aggregate_id UUID - event_type VARCHAR - payload JSONB -
created_at TIMESTAMPTZ - published_at TIMESTAMPTZ nullable - attempts
INTEGER default 0 - next_attempt_at TIMESTAMPTZ nullable - last_error
TEXT nullable - status VARCHAR (PENDING, PUBLISHED, FAILED) or derive
status from timestamps; choose one model consistently

Indexes: - (status, next_attempt_at, created_at) - (aggregate_id,
created_at)

A publisher should claim a small batch of pending rows safely, using a
row-locking strategy such as SELECT … FOR UPDATE SKIP LOCKED, then
publish. Be careful about transaction length: do not hold database locks
during long network calls if the design can avoid it. A robust pattern
is claim rows in a short transaction, publish, then mark results; or use
a lease/claim token. Duplicate publication is still possible if the
publisher crashes after Kafka accepts an event but before the database
records published_at. Therefore consumers must be idempotent.

5.7 worker_instances (optional durable registry) - id UUID or stable
worker ID - hostname VARCHAR - version VARCHAR - status VARCHAR -
last_heartbeat_at TIMESTAMPTZ - started_at TIMESTAMPTZ - metadata JSONB
nullable

Redis can provide fast live heartbeats, while PostgreSQL can store
durable worker session summaries if desired. Do not use two competing
sources of truth without defining which one is authoritative.

5.8 scheduled_jobs / recurring_jobs (stretch) For a simple
implementation, run_at on jobs plus a scheduler may be sufficient. For
recurring definitions, create recurring_job_definitions with id,
job_type, payload_template, cron_expression, timezone, enabled,
next_run_at, created_by, and timestamps. Ensure scheduler replicas do
not create duplicate instances. Use a unique scheduled occurrence key or
database lock/lease.

5.9 notification/external side-effect records (optional) If handlers
simulate notifications, keep results in attempts or a
notification_deliveries table. If real email/webhooks are ever added,
store delivery attempts, provider response IDs, and idempotency data
without exposing secrets.

======================================================================
6. API DESIGN
======================================================================

Use a consistent base path such as /api/v1. Return a standard error
structure. Use DTOs rather than exposing JPA entities directly. Validate
inputs with Bean Validation. Add pagination and bounded page sizes. Use
ISO-8601 timestamps in UTC.

6.1 Authentication POST /api/v1/auth/login Request: email, password
Response: access token, token type, expiry, minimal user profile Never
return password hashes.

Optional: POST /api/v1/auth/refresh only if refresh tokens are
deliberately implemented. Do not add refresh-token complexity unless you
can store and revoke them safely. A short-lived access token alone is
acceptable for a portfolio MVP.

6.2 Submit job POST /api/v1/jobs Headers: - Authorization: Bearer  -
Idempotency-Key: (or a field, but header is preferable) Request example:
{ “type”: “GENERATE_REPORT”, “queue”: “default”, “priority”: 5,
“payload”: {“reportName”: “weekly-summary”, “format”: “csv”}, “runAt”:
“2026-11-01T10:00:00Z”, “retryPolicy”: {“maxAttempts”: 3,
“initialDelaySeconds”: 5, “maxDelaySeconds”: 120} } Response: job ID,
status, createdAt, runAt, and whether an existing idempotent submission
was returned.

Rules: - Only registered handler types accepted. - Payload validation is
type-specific. - Max payload size enforced. - Priority and retry policy
have bounded values. - Client cannot set protected fields such as
status, attemptCount, createdBy, or result. - If the same idempotency
key and same request hash are submitted again, return the existing
job. - If the same key is reused with a different request hash, return
HTTP 409 Conflict. - Use a database unique constraint to close races
between concurrent submissions.

6.3 List jobs GET
/api/v1/jobs?status=FAILED&type=GENERATE_REPORT&queue=default&page=0&size=20&sort=createdAt,desc
Support filters for status, type, queue, created range, and optionally
tenant. Enforce a maximum page size. Never let the caller access jobs
outside their authorized scope.

6.4 Job detail GET /api/v1/jobs/{id} Return job metadata, current state,
safe payload summary if authorized, result if available, attempts
summary, and timestamps. Decide carefully whether payload and result
fields may contain sensitive data.

6.5 Attempts and events GET /api/v1/jobs/{id}/attempts GET
/api/v1/jobs/{id}/events Paginate event history if needed. Events should
be ordered chronologically, with a stable tie-breaker.

6.6 Retry POST /api/v1/jobs/{id}/retry Only allowed for eligible
terminal failure states. Requires authorization. Records who initiated
the retry. Preserve historical attempts. Define whether maxAttempts
resets for a manual retry cycle and document that behavior. Do not
silently erase error history.

6.7 Cancel POST /api/v1/jobs/{id}/cancel Allowed only for jobs not yet
executing, unless cooperative cancellation is implemented. Return a
clear conflict if cancellation is no longer possible. Never claim a
running Java method was forcibly terminated unless the handler actually
supports cooperative cancellation.

6.8 Worker status GET /api/v1/workers Return worker IDs, last heartbeat,
online/stale status, and non-sensitive metadata. Do not expose internal
secrets, environment variables, or infrastructure credentials.

6.9 Metrics / dashboard GET /api/v1/metrics/summary Return counts by
status, success/failure totals over a time window, queue depth,
average/p50/p95 duration if computed correctly, retry counts, and
active/stale worker counts. For small datasets, SQL aggregation may
suffice. Be transparent about the time window and sample size.

6.10 Health Use Spring Boot Actuator health endpoints for service
health. Keep liveness and readiness distinct if practical. A process
that is alive but cannot connect to PostgreSQL may be live but not ready
to accept work.

6.11 Error format Use a consistent shape such as: { “timestamp”: “…”,
“status”: 409, “code”: “IDEMPOTENCY_KEY_REUSED”, “message”: “The
idempotency key was already used with a different request.”, “path”:
“/api/v1/jobs” } Do not return stack traces, SQL messages, secrets, or
internal infrastructure details to clients. Log the exception with a
correlation ID.

Expected HTTP behavior: - 200 OK for successful reads/actions returning
a result. - 201 Created for new job submission. - 202 Accepted if
submission is accepted asynchronously and the API contract defines it
that way. - 400 Bad Request for invalid input. - 401 Unauthorized for
missing/invalid authentication. - 403 Forbidden for insufficient
permissions. - 404 Not Found for nonexistent or out-of-scope resources,
depending on information-disclosure policy. - 409 Conflict for invalid
state transition or idempotency conflict. - 429 Too Many Requests for
rate limits. - 500 for unexpected server errors without leaking
details. - 503 when a required dependency makes the requested operation
unavailable and graceful degradation is not possible.

======================================================================
7. SECURITY: JWT, RBAC, AND TENANT ISOLATION
======================================================================

7.1 Passwords Use BCrypt or Spring Security’s supported password
encoder. Normalize email addresses. Never log passwords. Seed demo
accounts only for local development and clearly document credentials. Do
not commit production secrets.

7.2 JWT Use a well-supported JWT library compatible with Spring
Security. Sign tokens with a secret from environment configuration for
local development, or a key pair if desired. Do not hard-code a
production secret in source code. Configure issuer, expiry, and signing
algorithm explicitly. Validate signature, expiration, and relevant
claims. Do not accept an algorithm chosen arbitrarily by the token.

Access tokens should be short-lived. A simple MVP may not implement
refresh tokens. Do not put sensitive information in JWT claims; signed
tokens are not automatically encrypted.

7.3 Roles Suggested roles: - ADMIN: manage users/settings and inspect
all jobs in the permitted tenant. - DEVELOPER: submit jobs and inspect
jobs they are permitted to access. - OPERATOR: inspect, retry, and
cancel jobs in the permitted scope. - VIEWER: read-only access.

Define a clear permission matrix. Apply authorization at API/service
level, not merely by hiding buttons in React. The frontend is not a
security boundary.

7.4 Tenant isolation If tenants are included, every job query and
mutation must be scoped by the authenticated principal’s tenant. Do not
fetch a job by ID globally and rely on the UI to hide it. Tests must
prove that one tenant cannot read, retry, or cancel another tenant’s
jobs.

7.5 CORS and browser security Configure CORS only for the known local
frontend origin(s) during development. Do not use wildcard origins with
credentialed requests. Store access tokens carefully; understand XSS
risks when choosing localStorage versus in-memory storage. For a
portfolio project, document the tradeoff rather than claiming perfect
security.

7.6 Rate limiting Use Redis for a token-bucket or fixed-window rate
limiter. Define the key scope, such as user ID, tenant ID, or API client
ID. Add expiry to rate-limit keys. Return 429 with an optional
Retry-After header. Decide how the API behaves when Redis is
unavailable: fail open for low-risk demo endpoints or fail closed for
abuse-sensitive submission endpoints, and document the decision. Avoid
accidental unbounded key creation.

7.7 Input and payload safety Validate JSON payloads by handler type. Set
request size limits. Reject unknown job types. Never execute
user-supplied code. Avoid logging entire payloads. Sanitize displayed
error messages. Do not deserialize arbitrary polymorphic Java types from
client-controlled JSON.

======================================================================
8. RABBITMQ: JOB DISPATCH AND WORKER EXECUTION
======================================================================

8.1 Purpose RabbitMQ delivers runnable job notifications from the
control plane to worker processes. PostgreSQL stores authoritative job
state. A RabbitMQ message should contain a job ID and minimal metadata,
not the only copy of the job payload.

Example message: { “messageId”: “UUID”, “jobId”: “UUID”, “tenantId”:
“UUID if applicable”, “jobType”: “GENERATE_REPORT”, “correlationId”:
“UUID”, “publishedAt”: “ISO-8601 timestamp” }

The worker should load the current job from PostgreSQL and validate that
it is eligible before execution. This avoids treating stale queue
messages as authoritative.

8.2 Queue and exchange Create a durable direct or topic exchange and
durable queues. Choose explicit names such as: -
queueforge.jobs.exchange - queueforge.jobs.default -
queueforge.jobs.high - queueforge.jobs.retry (if implementing
broker-level retry) - queueforge.jobs.dead Use routing keys that are
documented. Keep the topology small.

8.3 Acknowledgements Use manual acknowledgement semantics where
appropriate. A worker should acknowledge only after it has recorded a
durable outcome or has safely transferred responsibility. If it
acknowledges before recording the outcome, a crash can lose the job. If
it performs an external side effect and crashes before acknowledgement,
redelivery can duplicate the side effect. This is why idempotency
matters.

8.4 Worker algorithm 1. Receive message containing job ID. 2. Load job
from PostgreSQL. 3. If the job is already terminal, acknowledge the
stale message and stop. 4. If not eligible yet, reschedule or
acknowledge according to a documented dispatch strategy; do not spin
infinitely. 5. Claim the job with concurrency protection. Only one
worker should successfully claim a particular eligible attempt. 6.
Create a job_attempt record and set state RUNNING within a transaction.
7. Execute the registered handler outside the database transaction where
possible. Do not hold database row locks while doing slow work. 8. On
success, update attempt and job to SUCCEEDED, store a safe result
summary, create job event and outbox event in a transaction, then
acknowledge the RabbitMQ delivery. 9. On retryable failure, mark attempt
failed, compute nextAttemptAt, update job to RETRY_WAIT, record
event/outbox, and acknowledge only after responsibility for the retry is
durably represented. A scheduler/outbox-driven redispatch mechanism
should re-enqueue when due. 10. On permanent failure or exhausted
attempts, set FAILED or DEAD_LETTERED, record event/outbox, and
acknowledge. 11. On unexpected infrastructure failure before a durable
outcome, do not incorrectly mark the job successful. Allow safe
redelivery or use a lease/recovery strategy.

Avoid infinite immediate requeue loops. Use bounded retry delay and
backoff. Distinguish application retry policy from RabbitMQ’s own
dead-letter behavior. The database should explain why a job failed.

8.5 Job claiming and concurrency A possible approach: - Use a database
conditional update to change QUEUED to RUNNING only if current status is
still QUEUED, or use a row lock in a short transaction. - Check the
update count. If zero rows were updated, another worker or state
transition won; do not execute. - Use a version column or a guarded
transition. - Never assume RabbitMQ will deliver a message only once.

8.6 Retry strategy Store maxAttempts, attemptCount, initialDelay,
maxDelay, and optionally multiplier/jitter. An example exponential
policy: delay = min(maxDelay, initialDelay * 2^(attemptNumber - 1)) plus
bounded jitter. Protect against integer overflow and unreasonable
values. For deterministic tests, allow jitter to be disabled or inject a
predictable random source.

Retry categories: - Retryable: simulated timeout, temporary provider
outage, transient network issue. - Permanent: invalid payload,
unsupported job type, authorization/policy violation. - Unknown: define
conservative behavior and log correlation IDs.

8.7 Dead-letter handling Use a RabbitMQ dead-letter exchange/queue for
broker-level poison messages or repeated delivery failures, with bounded
broker retry policy. Application-level exhausted jobs must also become
terminal in PostgreSQL. Add an operator view for dead-lettered/failed
jobs and a safe manual replay action. Do not replay malformed messages
forever.

8.8 Worker process design Use a separate Spring Boot worker
application/module or a separate profile with distinct startup
configuration. The worker should have: - Stable worker ID (configurable
or generated per process) - Handler registry - Rabbit listener -
Heartbeat service - Graceful shutdown behavior - Execution timeout
policy where supported - Logs with job ID, attempt ID, worker ID,
correlation ID - Metrics for processed/succeeded/failed jobs

For the seven-day scope, a separate worker module is preferable if it
remains manageable. If build complexity threatens completion, use one
codebase with distinct control-plane and worker profiles while
preserving module boundaries.

======================================================================
9. KAFKA: LIFECYCLE EVENTS AND ANALYTICS
======================================================================

9.1 Purpose Kafka distributes lifecycle events to independent consumers
and demonstrates an event-streaming use case. RabbitMQ handles job
delivery; Kafka handles event distribution. Do not publish the same data
to both without a distinct purpose.

9.2 Event envelope Each event should have: - eventId UUID - eventType -
schemaVersion - aggregateType (JOB) - aggregateId (job ID) - tenantId if
applicable - occurredAt UTC timestamp - correlationId - producer -
payload containing safe, minimal event-specific fields

Example event: { “eventId”: “…”, “eventType”: “JobSucceeded”,
“schemaVersion”: 1, “aggregateType”: “JOB”, “aggregateId”: “…”,
“occurredAt”: “…”, “correlationId”: “…”, “payload”: { “jobType”:
“GENERATE_REPORT”, “durationMs”: 842 } }

Do not put passwords, JWTs, secrets, full sensitive payloads, or
unnecessary personal data into Kafka events.

9.3 Topics Start with one topic such as queueforge.job-events. Partition
by job ID to preserve ordering for a particular job within a partition.
Explain that ordering is per partition, not global. Add separate topics
only when a clear use case exists.

9.4 Transactional outbox implementation When a domain transition is
committed: - Update job state. - Update attempt/event history. - Insert
outbox event. All three happen in the same PostgreSQL transaction.

Publisher: 1. Poll a bounded batch of pending outbox rows. 2. Claim them
safely so multiple publisher instances do not all work on the same row
unnecessarily. 3. Publish the event to Kafka using event ID as a stable
identifier and job ID as the key. 4. Wait for broker acknowledgement
according to producer configuration. 5. Mark the outbox row published.
6. On failure, increment attempts, record a sanitized error, and
schedule a bounded retry. 7. Provide visibility into outbox backlog and
oldest pending age.

There is an unavoidable failure window: Kafka may accept an event and
the publisher may crash before marking the outbox row published. The
event may be sent again. Therefore event consumers should be idempotent,
for example by recording processed event IDs or making updates naturally
idempotent.

Do not hold a PostgreSQL transaction open across long Kafka network
calls unless there is a carefully justified design. A simple portfolio
implementation can claim a row/lease in a short transaction, publish,
and then update the row. Document duplicate-publication behavior.

9.5 Consumers Create at least one useful consumer, such as analytics
aggregation or an activity projection. It should: - Deserialize with
schema/version validation. - Handle unknown event types safely. - Use a
consumer group. - Avoid acknowledging/committing an offset before
required processing is durable. - Handle duplicate event IDs. - Log
failures with event ID and correlation ID. - Avoid blocking a partition
indefinitely due to poison messages.

For the MVP, an analytics consumer can update aggregate counters or a
job-event projection. If you create a dashboard query directly against
PostgreSQL, explain what Kafka is used for (e.g., asynchronous analytics
projection) and demonstrate that consumer.

9.6 Kafka configuration Use appropriate local bootstrap servers in
Docker and host-accessible bootstrap addresses for applications running
on the host. Misconfigured advertised listeners are a common Docker
problem. Verify connectivity from the actual container/network where
each app runs. Use explicit consumer group IDs and
serializers/deserializers. Do not suppress deserialization errors
silently.

======================================================================
10. REDIS: CACHING, RATE LIMITING, AND WORKER PRESENCE
======================================================================

Redis should have at least one concrete purpose. Use two or three small,
justified features rather than turning Redis into a second database.

10.1 Rate limiting Implement a simple per-user or per-tenant limit for
job submissions. A fixed-window counter is easiest, but it can burst at
window boundaries. A token bucket or sliding-window algorithm is more
accurate but more complex. For the portfolio version: - Choose the
algorithm. - Set a maximum request rate and window. - Increment
atomically, preferably with a Lua script or safe Redis operation
sequence. - Apply key expiry. - Return 429 when over limit. - Add tests
for allowed requests, rejected requests, expiry, and Redis-unavailable
behavior.

10.2 Worker heartbeat Each worker periodically writes a key such as
worker:{workerId}:heartbeat with a short TTL and non-sensitive metadata.
The dashboard considers a worker online only while its heartbeat is
fresh. On graceful shutdown, it can remove its key; TTL handles crashes.

Avoid storing secrets or full environment details in worker metadata.
Use a namespace prefix to avoid collisions.

10.3 Caching Optional: cache a frequently requested dashboard summary.
Use a short TTL and define invalidation behavior. Avoid caching mutable
job detail in a way that makes the dashboard misleading. Cache keys must
include tenant/scope where relevant. A cache hit must never bypass
authorization.

10.4 Redis outage Decide the behavior per feature: - Cache unavailable:
bypass cache and query PostgreSQL if safe. - Heartbeat unavailable:
worker can continue only if doing so doesn’t violate a safety policy;
report monitoring degradation. - Rate limiter unavailable: choose and
document fail-open or fail-closed. Do not allow a Redis outage to erase
durable job records.

======================================================================
11. SCHEDULING, DELAYED JOBS, AND RECURRING JOBS
======================================================================

11.1 Delayed jobs A job with runAt in the future starts as SCHEDULED. A
scheduler periodically selects due jobs in bounded batches and
transitions them to QUEUED. Use a database claim strategy so multiple
scheduler instances do not enqueue the same job repeatedly. The
operation should be idempotent.

A simple implementation uses: - Query due SCHEDULED jobs. - Claim rows
using a short transaction and row locks (e.g., SKIP LOCKED where
appropriate). - Transition claimed jobs to QUEUED and record an
event/outbox entry. - Dispatch through a durable outbox/dispatcher
mechanism. - Ensure a crash after state change but before RabbitMQ
publication does not strand the job. Prefer a database dispatch-outbox
pattern or a reconciliation poller.

11.2 Retry scheduling When an attempt fails retryably: - Set
RETRY_WAIT. - Calculate nextAttemptAt. - Record the failure and
attempt. - At the due time, transition to QUEUED and dispatch. Do not
sleep a worker thread for minutes while waiting to retry.

11.3 Recurring jobs (stretch) A recurring definition generates distinct
job instances for each scheduled occurrence. Each occurrence needs a
stable unique key such as recurringDefinitionId plus scheduledTime.
Prevent duplicate occurrence creation if the scheduler runs twice.
Define timezone semantics and daylight-saving behavior. Store cron
expression and timezone explicitly. Validate cron expressions on input.

For a seven-day implementation, delayed jobs are more important than
recurring jobs. Implement recurring scheduling only after delayed jobs
and retry scheduling are reliable.

======================================================================
12. PRIORITY, CONCURRENCY, AND FAIRNESS
======================================================================

Priority: - Use a bounded integer range (e.g., 1-10). - Define whether a
higher number means higher priority. - Ensure the dashboard and API use
the same convention. - RabbitMQ queue ordering alone does not guarantee
global priority across separate queues. If multiple queues are used,
document the dispatch policy. - Avoid claiming strict priority fairness
without tests.

Concurrency: - Support a configurable worker concurrency limit. -
Optional per-tenant concurrency limit to prevent one tenant monopolizing
workers. - Use safe database claims and/or distributed coordination; do
not use a local Java lock as if it coordinated multiple processes. - If
Redis leases are used, account for expiry, ownership tokens, and release
safety. For the MVP, database conditional state transitions are often
simpler and safer.

Fairness: A practical MVP may use separate queues or a simple scheduler.
Do not build a sophisticated weighted-fair scheduler in seven days. If
priorities are implemented, create tests that prove the intended
ordering in a controlled scenario.

======================================================================
13. HANDLER REGISTRY AND EXECUTION CONTRACT
======================================================================

Define a common interface, conceptually: - supports(jobType) -
validate(payload) - execute(context, payload) -
classifyException(exception) - optionally produce a safe result summary

Use a registry/map of explicit handlers. Do not use reflection to
instantiate arbitrary classes from client input.

Example handlers:

SEND_EMAIL (simulated): - Validate recipient and subject fields. - Do
not send real email by default. - Simulate success/failure
deterministically based on test configuration, not on dangerous payload
tricks. - Return a safe result such as { “delivery”: “SIMULATED”,
“messageId”: “…” }.

GENERATE_REPORT: - Validate report type and date range. - Generate a
small deterministic report summary or CSV file within a configured local
output directory if file generation is implemented. - Avoid unbounded
memory use or arbitrary path writes. Do not let clients choose arbitrary
filesystem paths.

PROCESS_WEBHOOK (simulated): - Validate a URL only if actual HTTP
delivery is enabled. - In the default MVP, simulate delivery. - If real
HTTP calls are added, defend against SSRF: block
loopback/private/link-local destinations, validate DNS/address
resolution, restrict protocols, set timeouts, limit redirects, and do
not forward secrets. This feature is optional and should not be rushed.

Handler execution contract: - Handlers should be bounded and have
timeouts where practical. - Keep slow work outside database
transactions. - Return structured success/error outcomes. - Distinguish
retryable from permanent errors. - Record enough information for
debugging but never expose secrets. - A handler should not independently
mutate job status; the execution service owns lifecycle transitions.

======================================================================
14. FRONTEND DASHBOARD SPECIFICATION
======================================================================

Build a clear, responsive operational dashboard. Prefer a functional,
polished interface over decorative complexity. Use React + TypeScript +
Vite. Use a consistent component library or a small coherent CSS system;
avoid spending a full day changing visual styles.

14.1 Login page - Email/password form. - Validation and clear errors. -
Store token according to the documented frontend security choice. -
Redirect to dashboard after successful login. - Handle expired/invalid
token by returning to login. - Do not expose secrets in frontend
environment variables.

14.2 Overview page Cards: - Total jobs in selected time window. -
Queued/running jobs. - Succeeded/failed jobs. - Retry-wait jobs. -
Active/stale workers. - Pending outbox events if exposed.

Charts/tables: - Job throughput over time. - Success vs failure
counts. - Recent failed jobs. - Queue depth. - Average or percentile
duration only when calculated correctly.

Do not use hard-coded sample numbers in the final demo. Seeded sample
jobs are acceptable if labeled as demo data.

14.3 Jobs table Columns: - Job ID (shortened visually, copy full ID
action) - Type - Status - Priority - Attempts / max attempts - Created
time - Scheduled/next attempt time - Duration where available - Actions
based on permissions and state

Features: - Filter by status, type, queue, date range. - Search by job
ID or supported metadata if safe. - Server-side pagination. - Loading,
empty, and error states. - Clear indication when data is stale.

14.4 Job detail page - Summary with status, type, timestamps, attempt
count, priority. - Safe payload/result view. - Attempt history with
duration and error details. - Lifecycle event timeline. - Retry button
for eligible jobs. - Cancel button only when allowed. - Correlation ID
and copy-ID actions. - Clear confirmation for destructive/irreversible
actions.

14.5 Workers page - Worker ID. - Online/stale status. - Last
heartbeat. - Processed/succeeded/failed counters if available. - Avoid
exposing machine secrets.

14.6 UI behavior - Use API clients with centralized base URL and auth
handling. - Do not duplicate API calls unnecessarily. - Show actionable
errors but not backend stack traces. - Protect routes for usability,
while still enforcing authorization on backend. - Handle loading and
empty states. - Ensure tables remain usable on laptop-sized screens. -
Add a seeded demo login to README for local use only.

14.7 Optional SSE SSE can stream lifecycle updates to the browser. Only
add it after ordinary polling and API flows work. Authenticate the
stream safely. Do not leak cross-tenant events. Handle reconnects and
avoid treating SSE as the source of truth; the REST API remains
authoritative.

======================================================================
15. OBSERVABILITY AND OPERATIONS
======================================================================

15.1 Logging Use structured logs where practical. Include: - timestamp -
level - service/process - jobId - attemptId - workerId - eventId -
correlationId - operation - outcome Do not log passwords, JWTs, secrets,
full sensitive payloads, or unfiltered exception data returned to
clients.

15.2 Metrics At minimum, expose or compute: - jobs submitted - jobs
succeeded/failed - retries - execution duration - queue depth - outbox
backlog - worker heartbeat freshness - dispatch failures - handler error
categories

Define each metric carefully. For example, average execution duration
should exclude jobs that have not finished. Percentiles need a
meaningful sample window and method. Don’t label a rough SQL estimate as
an exact percentile.

15.3 Health checks - Liveness: process is running. - Readiness: the
service can safely accept its intended work. - Dependency health:
PostgreSQL, Redis, RabbitMQ, Kafka status where practical. Avoid making
liveness fail merely because a transient dependency is down if that
causes restart loops. Make readiness and operational status informative.

15.4 Correlation IDs Generate or validate a correlation ID at the API
boundary. Carry it through the database job, RabbitMQ message, Kafka
event, worker logs, and UI. Never trust arbitrary unbounded strings;
validate length and characters.

15.5 Failure visibility Provide a way to see: - failed jobs and last
safe error - attempt count and retry schedule - pending outbox event
age - stale workers - RabbitMQ queue backlog where feasible - Kafka
consumer errors where feasible

======================================================================
16. PROJECT STRUCTURE
======================================================================

Prefer a Maven multi-module project if it stays understandable: -
queueforge-common: shared DTO/event models and utilities (avoid
over-sharing persistence entities) - queueforge-api: REST API, security,
job services, PostgreSQL repositories, outbox publisher -
queueforge-worker: RabbitMQ consumer and handler runtime -
queueforge-frontend: React application - infra: Docker Compose and
infrastructure configuration - docs: architecture diagrams, decisions,
API examples, demo plan

A simpler single Maven module with distinct packages and a worker
profile is acceptable if multi-module setup slows progress. Make one
deliberate decision and document it.

Possible API package organization: com.queueforge config auth users
tenants jobs api application domain persistence attempts events outbox
dispatch workers metrics common handlers

Keep controllers thin. Business logic belongs in application/domain
services. Repositories handle persistence. DTOs isolate the external API
from internal persistence models. Do not put all code in one enormous
service class.

======================================================================
17. STEP-BY-STEP IMPLEMENTATION PLAYBOOK
======================================================================

PHASE 0 — Repository and planning

1.  Create a Git repository and add this specification as
    docs/QUEUEFORGE_SPEC.md.
2.  Add a README with the product purpose and initial setup outline.
3.  Ask the AI assistant to summarize the spec and list ambiguities
    before coding.
4.  Ask it to propose an implementation plan and a dependency/version
    matrix.
5.  Decide: Java version, Spring Boot version, Maven structure, worker
    packaging, frontend approach, and local Docker topology.
6.  Create docs/architecture.md and docs/decisions.md. Record key
    choices and tradeoffs.
7.  Add .gitignore for Java, Maven, Node, IDE files, environment files,
    generated reports, and secrets.
8.  Commit the skeleton before generating all features.

PHASE 1 — Infrastructure

1.  Create Docker Compose services for PostgreSQL, Redis, RabbitMQ
    management UI, Kafka, and the chosen Kafka dependency setup (e.g.,
    KRaft-compatible image/configuration).
2.  Use health checks where supported.
3.  Use named volumes for local persistence.
4.  Use a dedicated Docker network.
5.  Put credentials and ports in environment variables or a local .env
    file that is gitignored. Commit only .env.example with fake local
    values.
6.  Configure separate internal and host-accessible Kafka listener
    addresses correctly.
7.  Start infrastructure and verify each service independently before
    starting Spring Boot.
8.  Document the local URLs and credentials for development tools. Never
    use these defaults in production.
9.  Add an infrastructure troubleshooting section: port conflicts, Kafka
    advertised listeners, Docker DNS names, volume reset instructions,
    and health-check status.

PHASE 2 — Spring Boot skeleton and database

1.  Generate the project with Web, Validation, Security, Data JPA,
    PostgreSQL driver, Flyway, Actuator, AMQP, Kafka, Redis, and testing
    dependencies as needed.
2.  Add configuration properties classes and validate required
    environment variables at startup.
3.  Configure separate profiles for local/test where helpful.
4.  Add Flyway V1 migration for users, jobs, attempts, job events,
    outbox events, and indexes.
5.  Add entities and repositories; use UUIDs and UTC timestamps.
6.  Use DTOs and mapping code. Avoid returning entities directly.
7.  Set schema management to validate against Flyway-managed schema.
8.  Add a context-load test and repository integration test.
9.  Confirm the app can start, connect to PostgreSQL, and execute a
    migration.
10. Commit.

PHASE 3 — Authentication and authorization

1.  Implement password hashing.
2.  Implement login endpoint and JWT generation/validation.
3.  Implement a Spring Security filter chain and authentication
    principal.
4.  Define role and permission policy.
5.  Add seeded local demo users through a controlled development
    mechanism.
6.  Add protected endpoint tests: no token, malformed token, expired
    token, valid role, insufficient role.
7.  If multi-tenant mode is enabled, ensure principal scope is applied
    in repositories/services.
8.  Verify frontend/API CORS behavior.
9.  Commit.

PHASE 4 — Job submission and queries

1.  Define job request/response DTOs.
2.  Define handler types and type-specific payload validators.
3.  Implement POST /jobs with size limits and validation.
4.  Implement idempotency-key logic with request hashing and database
    uniqueness.
5.  Implement GET list with server-side filters and pagination.
6.  Implement GET detail, attempts, and event timeline.
7.  Add structured API errors and correlation IDs.
8.  Add tests for valid submission, invalid type, invalid payload,
    repeated key, changed payload with same key, paging, and access
    control.
9.  Commit.

PHASE 5 — Lifecycle state machine and transactional persistence

1.  Implement a domain service for state transitions.
2.  Define an explicit allowed-transition table.
3.  Add version/conditional-update protection.
4.  Create job event and outbox records in the same transaction as every
    significant state change.
5.  Record each attempt separately.
6.  Implement cancellation and manual retry only for documented states.
7.  Test illegal transitions and two competing updates.
8.  Ensure error messages do not leak internal data.
9.  Commit.

PHASE 6 — Handler registry and synchronous local execution test

1.  Create the handler interface.
2.  Implement SEND_EMAIL, GENERATE_REPORT, and PROCESS_WEBHOOK
    simulation handlers.
3.  Validate payloads with strict bounds.
4.  Define retryable/permanent error categories.
5.  Add deterministic test handlers that can succeed, fail retryably,
    fail permanently, or delay for a controlled duration.
6.  Keep test-only failure controls unavailable in production
    configuration.
7.  Test handler outputs and exception classification.
8.  Commit.

PHASE 7 — RabbitMQ dispatch and worker

1.  Declare exchange, queues, routing keys, and dead-letter topology
    through application configuration or infrastructure definitions.
2.  Implement the dispatcher to publish job ID messages.
3.  Make publication durable and handle publisher failures.
4.  Implement worker listener with manual acknowledgement or an
    equivalent reliable container strategy.
5.  On receipt, load the job from PostgreSQL and claim it with a guarded
    state transition.
6.  Create attempt record and mark RUNNING in a short transaction.
7.  Execute handler outside the database transaction.
8.  Persist outcome, attempt, job event, and outbox event
    transactionally.
9.  Acknowledge only after durable outcome or durable retry
    responsibility exists.
10. Implement bounded retry scheduling and terminal failure behavior.
11. Add redelivery/duplicate-message tests.
12. Run two worker instances and verify a job is not concurrently
    claimed twice.
13. Commit.

PHASE 8 — Outbox publisher and Kafka

1.  Configure Kafka topic and producer.
2.  Define a versioned event envelope.
3.  Implement outbox row claiming and publishing.
4.  Record published state only after broker acknowledgement.
5.  Add retry/backoff for publisher failures.
6.  Implement one consumer, preferably an analytics projection or event
    audit projection.
7.  Make the consumer idempotent using event ID or naturally idempotent
    updates.
8.  Test Kafka unavailable/recovery and duplicate event handling where
    feasible.
9.  Display pending outbox count/oldest age.
10. Commit.

PHASE 9 — Redis

1.  Choose and document the rate-limit algorithm.
2.  Implement atomic counter/token logic with TTL.
3.  Integrate it with job submission.
4.  Add worker heartbeat with TTL and a stable worker ID.
5.  Expose worker list/online status.
6.  Optionally cache dashboard summary with a short TTL and correct
    scope-aware keys.
7.  Test expiry and behavior during Redis outage.
8.  Commit.

PHASE 10 — Delayed/retry scheduling

1.  Add runAt and nextAttemptAt fields if not already present.
2.  Implement bounded polling of due jobs.
3.  Claim due rows safely when multiple scheduler instances are
    possible.
4.  Transition due jobs and dispatch them without losing them if a
    process crashes.
5.  Use stable occurrence/idempotency identifiers for recurring work if
    added.
6.  Test future jobs do not run early, due jobs eventually dispatch,
    retries wait, and repeated scheduler runs do not create duplicates.
7.  Commit.

PHASE 11 — Frontend

1.  Scaffold React + TypeScript + Vite.
2.  Add routing, API client, login, and auth handling.
3.  Build overview metrics cards.
4.  Build jobs table with filters and pagination.
5.  Build job detail page with attempt/event timeline.
6.  Add retry/cancel actions with state-based enablement and
    confirmation.
7.  Build worker status page.
8.  Add loading, empty, error, and stale-data states.
9.  Connect to real backend endpoints. Remove fake dashboard metrics
    before final demo.
10. Test the core user journey manually.
11. Commit.

PHASE 12 — Tests, reliability, and security review

1.  Unit tests for lifecycle transitions, retry calculation, validation,
    handler classification, and authorization policy.
2.  Integration tests for PostgreSQL repositories and transaction
    boundaries.
3.  RabbitMQ tests for delivery, retry, and duplicate messages.
4.  Kafka/outbox tests for publication, duplicate delivery, and consumer
    idempotency.
5.  API tests for authentication, validation, pagination, tenant
    scoping, rate limits, and state conflicts.
6.  Add at least one worker-crash/abandoned-execution recovery test or
    reproducible manual demo.
7.  Run dependency checks and remove unused dependencies.
8.  Search the repository for hard-coded secrets and unsafe logging.
9.  Verify no API returns stack traces or sensitive payloads.
10. Run tests from a clean checkout if possible.
11. Commit.

PHASE 13 — Documentation and demonstration

1.  Update README with purpose, features, architecture, prerequisites,
    startup steps, seeded accounts, API examples, environment variables,
    and troubleshooting.
2.  Add architecture diagram and sequence diagrams for submission,
    success, retry, and recovery.
3.  Add OpenAPI docs and representative curl examples.
4.  Record exact test commands and results.
5.  Run a repeatable load test at a modest, documented level and report
    actual results with hardware/environment details.
6.  Add known limitations and future improvements.
7.  Record a 2–4 minute demo showing job submission, worker processing,
    failure/retry, dashboard history, and infrastructure.
8.  Verify the project can be started from a clean state using
    documented commands.
9.  Tag a release and ensure the GitHub repository has a clear
    description.

======================================================================
18. TEST PLAN: REQUIRED SCENARIOS
======================================================================

Authentication: - Login succeeds with correct credentials. - Wrong
password fails. - Missing, malformed, expired, or invalid-signature JWT
is rejected. - Role restrictions are enforced. - Cross-tenant access is
denied if tenants are enabled.

Submission: - Valid job creates one durable job. - Unknown handler type
is rejected. - Invalid payload is rejected. - Payload too large is
rejected. - Same idempotency key and same request returns existing
job. - Same key with different request returns 409. - Two concurrent
submissions with same key still create one logical job.

State machine: - Valid transitions succeed. - Invalid transitions fail
with a conflict. - Terminal success cannot be accidentally rerun. -
Cancel after start behaves according to the documented policy. - Manual
retry preserves prior attempt history. - Concurrent claims result in
only one successful claim.

Execution: - Successful handler reaches SUCCEEDED. - Retryable failure
reaches RETRY_WAIT and is retried after the delay. - Permanent failure
does not retry. - Exhausted retries reach FAILED or DEAD_LETTERED
consistently. - Handler result/error is persisted safely. - A duplicate
RabbitMQ message does not cause an invalid second execution. - A worker
crash leaves recoverable state. - A stale message for a terminal job is
safely acknowledged.

Outbox/Kafka: - State transition and outbox row commit atomically. -
Publisher sends pending event. - Publisher retries on broker failure. -
Crash after Kafka publish but before marking outbox row can create
duplicate publication; consumer handles duplicates. - Unknown event type
is handled safely. - Event consumer records/handles processed IDs
correctly.

Redis: - Rate limit permits allowed traffic and rejects excess. -
Counter expires or replenishes as designed. - Cache key includes the
correct scope. - Redis outage behavior matches documentation. -
Heartbeat expires after worker termination.

Scheduling: - Future job does not execute early. - Due job is eventually
queued. - Retry doesn’t happen before nextAttemptAt. - Scheduler reruns
don’t create duplicate occurrences. - Two scheduler instances don’t both
claim the same row incorrectly.

Frontend: - Login works. - Unauthorized API errors are handled. -
Filters and pagination work. - Detail page shows attempts and events. -
Retry/cancel buttons appear only when appropriate. - Dashboard numbers
come from actual API responses. - Empty/error/loading states render
correctly.

======================================================================
19. LOAD TESTING AND MEASUREMENT
======================================================================

Do not invent impressive numbers for the README or resume. Use a modest
reproducible test and record the environment.

Possible load-test tools: k6, Apache JMeter, or a simple controlled
script. Pick one tool and keep the test reproducible.

Measure: - Job submission requests per second. - HTTP p50/p95/p99
latency. - Jobs completed per second. - Queue backlog under sustained
input. - Time from submission to start. - Execution duration by handler
type. - Retry rate and failure rate. - CPU and memory of the local
processes. - Outbox backlog and publication lag.

Test at several controlled concurrency levels, for example low, medium,
and higher concurrency appropriate to the laptop. Do not overload a
laptop and then generalize the result to production-scale hardware.
State whether the test ran with all services on one machine and whether
handlers simulate work.

Distinguish: - API throughput: how fast jobs can be accepted. - Worker
throughput: how fast handlers complete. - End-to-end latency: submission
to final outcome. - Queue latency: time waiting before execution. These
are different metrics.

======================================================================
20. DOCKER COMPOSE AND LOCAL DEVELOPMENT
======================================================================

The Compose stack should include: - postgres - redis - rabbitmq with
management UI - kafka (and any required coordination service only if the
chosen distribution requires it) - queueforge-api - queueforge-worker
(one or more instances) - queueforge-frontend (optional container;
running Vite locally is also fine)

Rules: - Use service DNS names from containers, not localhost, for
inter-container traffic. - Use correct Kafka advertised listeners for
both internal containers and host clients. - Add health checks and
startup dependencies, but do not assume depends_on alone proves a
service is ready. - Use named volumes for persistent local data. -
Provide a documented command to reset local data. Warn that it deletes
development state. - Expose only the ports needed for local
development. - Never commit real secrets. - Keep container logs readable
and make service names clear. - Ensure the app retries transient startup
connections where reasonable. - Document memory constraints, especially
when running Kafka and all services on a laptop.

The README should distinguish: - commands run from the host - commands
run inside a container - internal Docker hostnames - browser URLs

======================================================================
21. ERROR HANDLING AND RESILIENCE POLICIES
======================================================================

For every dependency, define what happens when it is unavailable.

PostgreSQL: - Critical for durable job state. If unavailable, do not
accept jobs as successfully persisted. - Return a safe
service-unavailable error when a required write cannot occur. - Never
silently fall back to in-memory-only job records.

RabbitMQ: - If dispatch fails after a job is persisted, the job must
remain recoverable. - Prefer a durable dispatch/outbox pattern or a
reconciliation poller that identifies eligible QUEUED jobs not yet
dispatched. - Avoid marking a job permanently failed merely because the
broker was temporarily unavailable. - Prevent endless duplicate dispatch
from reconciliation.

Kafka: - If Kafka is down, domain state transitions can still commit if
the outbox row is committed with them. - Outbox backlog should increase
and be visible. - Resume publication when Kafka returns.

Redis: - Rate limiter/cache/heartbeat behavior follows the explicitly
documented degradation policy. - PostgreSQL remains the durable source
for jobs.

Worker crash: - A RUNNING job may be abandoned. Use a lease/heartbeat or
execution timeout/reconciliation policy. - Recovery must avoid
immediately duplicating a still-running job merely because one heartbeat
was delayed. - A recovered job should record the abandoned attempt and
create a new attempt if retried. - A handler that already performed an
external side effect may run again; use idempotency at the side-effect
boundary.

Frontend: - Show a useful error and allow retry of read requests where
safe. - Do not blindly retry non-idempotent submissions without an
idempotency key.

======================================================================
22. IMPORTANT DISTRIBUTED-SYSTEMS LIMITATIONS
======================================================================

22.1 Exactly-once execution Do not claim QueueForge guarantees
exactly-once execution. A worker may complete an external side effect
and crash before persisting success. Redelivery may execute the handler
again. Practical design uses at-least-once delivery, idempotent
handlers, idempotency keys, guarded state transitions, and deduplication
records.

22.2 Database and broker atomicity A normal PostgreSQL transaction
cannot atomically commit both a database row and a RabbitMQ/Kafka
publication. The outbox pattern bridges this gap, but publication can be
duplicated. Consumers must be idempotent.

22.3 Redis locks A distributed lock is not automatically safe simply
because Redis supports SET NX. Leases can expire while a process is
still working, clocks and pauses complicate timing, and release must
verify ownership. Prefer database conditional claims for job state. Add
Redis locking only when needed and explain its limitations.

22.4 Retries Retries can amplify an outage. Use bounded attempts,
exponential backoff, jitter where appropriate, and a terminal failure
path. Retry only errors that might recover.

22.5 Cancellation A job that is already running cannot necessarily be
stopped safely. Support cancellation before execution first. Cooperative
cancellation requires handlers to check a cancellation signal and safely
stop.

22.6 Priority Priority is a policy, not a magical broker setting.
Explain whether priority is enforced by database dispatch order,
separate queues, or RabbitMQ queue configuration. Test the policy.

22.7 Observability Metrics are only useful when definitions are clear.
Do not report success rate without defining the denominator or time
range. Do not infer worker health from a process merely existing; use
heartbeats and explain the freshness threshold.

======================================================================
23. AI CODING ASSISTANT WORKING AGREEMENT
======================================================================

The AI assistant must follow these rules throughout implementation:

1.  Read this entire document before generating code. Summarize the
    intended architecture, core flows, and risks first.
2.  Do not attempt to generate the entire repository in one enormous
    response. Work phase by phase and keep each change testable.
3.  Before editing, inspect the existing project structure and current
    code. Do not overwrite working files blindly.
4.  Produce a short plan for each phase: files to add/change, design
    choice, tests to run, and acceptance criteria.
5.  Implement one vertical slice at a time. Prefer a compiling, tested
    feature over many unverified files.
6.  After each phase, run the relevant Maven tests/build and frontend
    lint/build commands. If tools are unavailable, state that clearly
    and give exact commands for the user to run.
7.  Never say tests passed unless they were actually executed and
    passed. Distinguish static reasoning from observed results.
8.  When a command fails, inspect the complete error, identify the root
    cause, make a targeted fix, and rerun the failing command. Do not
    hide errors or disable tests merely to get a green build.
9.  Do not delete or weaken tests to make implementation pass.
10. Do not use insecure shortcuts such as plaintext passwords,
    hard-coded production secrets, disabled security filters, wildcard
    CORS with credentials, arbitrary code execution, or returning full
    exception stack traces.
11. Do not introduce new libraries unless they solve a clear need.
    Explain each substantial dependency.
12. Keep API DTOs separate from JPA entities. Keep controllers thin and
    business logic in services/domain components.
13. Keep all persistent schema changes in Flyway migrations. Do not rely
    on manual database edits.
14. Keep job state transitions centralized and validated.
15. Preserve idempotency and concurrency protections. Do not replace
    them with local in-memory maps.
16. Treat PostgreSQL as the durable source of truth. Redis is not the
    only copy of critical state.
17. Use RabbitMQ for task delivery and Kafka for lifecycle event
    streaming, with distinct responsibilities.
18. Use a transactional outbox for Kafka lifecycle events. For RabbitMQ
    dispatch, ensure a persisted eligible job can be recovered if broker
    publication fails.
19. Design consumers and workers for duplicate delivery. Never claim
    exactly-once side effects.
20. Keep configuration externalized. Provide .env.example with
    placeholders; never commit secrets.
21. Ensure logs and API responses do not leak secrets or sensitive
    payloads.
22. Write meaningful tests for failure cases, not only happy paths.
23. Update README, API examples, architecture docs, and known
    limitations when behavior changes.
24. Do not claim a performance number until the test has been run and
    its conditions recorded.
25. When a requirement is ambiguous, choose the simplest safe
    implementation, document the choice, and ask the user only if the
    ambiguity blocks progress.
26. Maintain a project checklist with statuses: NOT STARTED, IN
    PROGRESS, BLOCKED, DONE. DONE means verified, not merely
    code-generated.
27. At the end of every session, report:

-   files changed
-   features implemented
-   commands executed
-   actual test/build outcomes
-   known bugs/risks
-   next recommended step

28. Never silently change core architecture. Propose a change and
    explain its tradeoff first.
29. Avoid giant generated files and overly generic abstractions. Prefer
    understandable code that the user can explain in an interview.
30. Teach while building: for every major mechanism, add a concise
    explanation of what it does, why it exists, and how to debug it.

======================================================================
24. ACCEPTANCE CRITERIA BY MILESTONE
======================================================================

Milestone A — Foundation: - Compose starts dependencies. - API starts
and connects to PostgreSQL. - Flyway migrations apply. - Health endpoint
works.

Milestone B — Secure API: - Login returns a valid access token. -
Protected routes reject unauthenticated requests. - Roles are
enforced. - Job submission validates type/payload.

Milestone C — Durable jobs: - Jobs persist and can be listed/read. -
Idempotency works under repeated and concurrent requests. - State
transitions and event history are consistent.

Milestone D — Worker: - A submitted job is dispatched and executed. -
Attempt records are created. - Success and failure outcomes persist. -
Duplicate delivery is safe.

Milestone E — Retry/recovery: - Retryable failures wait and retry. -
Permanent failures do not retry. - Exhausted jobs become terminal. -
Abandoned work can be detected/reconciled.

Milestone F — Kafka/outbox: - Job transition creates outbox event in
same transaction. - Publisher sends event to Kafka. - Consumer processes
events idempotently. - Broker outage does not silently lose committed
lifecycle events.

Milestone G — Redis: - Rate limit works. - Worker heartbeat expires. -
Redis outage behavior is documented and tested where feasible.

Milestone H — Frontend: - Login, dashboard, jobs table, details,
attempts, events, retry/cancel, and worker status use real API data. -
Errors and empty states are handled.

Milestone I — Delivery: - Clean setup instructions work. - Automated
tests pass. - Architecture and limitations are documented. - Demo is
reproducible. - No invented metrics or claims.

======================================================================
25. DEMO SCRIPT
======================================================================

Use this sequence for a concise project demo:

1.  Start QueueForge using the documented Compose command.
2.  Open the dashboard and log in with a development demo account.
3.  Submit a GENERATE_REPORT job using the UI or curl.
4.  Show the job in QUEUED/RUNNING and then SUCCEEDED.
5.  Open its detail page and show attempt and lifecycle history.
6.  Submit a test job configured through a test-only handler to fail
    transiently once, then succeed. Show RETRY_WAIT, the later attempt,
    and final success.
7.  Demonstrate idempotent submission by repeating the same request with
    the same key and showing that no second logical job is created.
8.  Stop a worker during a controlled test job and show the documented
    recovery behavior. Avoid presenting a race-prone manual demo as a
    guarantee; rehearse it and use an automated test as proof.
9.  Show RabbitMQ management UI and Kafka event/consumer logs.
10. Show the outbox backlog and explain what happens if Kafka is
    temporarily unavailable.
11. Show worker heartbeat status in the dashboard.
12. Show the test report and load-test results with actual conditions.
13. End with architecture, limitations, and future work.

Do not show secrets, local tokens, or sensitive machine details in the
recording.

======================================================================
26. README OUTLINE
======================================================================

README.md should contain: 1. Project title and short description. 2.
Problem statement and use cases. 3. Feature list separated into
implemented and planned. 4. Architecture diagram. 5. Tech stack and why
each component is used. 6. Architecture/data-flow explanation. 7.
Prerequisites. 8. Environment setup and .env.example. 9.
Start/stop/reset commands. 10. Demo credentials for local development
only. 11. API docs URL and curl examples. 12. Job lifecycle and retry
policy. 13. Tests and how to run them. 14. Load-test method and actual
measured results. 15. Screenshots or short GIF. 16. Known limitations
and failure semantics. 17. Security notes. 18. Roadmap. 19. License, if
chosen.

Never list a feature as implemented merely because its code exists.
Verify it first.

======================================================================
27. FUTURE EXTENSIONS AFTER THE SEVEN-DAY MVP
======================================================================

Only consider these after the core is stable: - Recurring cron jobs with
timezone support. - Workflow DAGs and dependencies between jobs. -
Multi-tenant quotas and billing. - Priority/fair scheduling. -
Per-tenant concurrency limits. - Job payload encryption and retention
policies. - External object storage for large artifacts. - Webhook
signing and replay protection. - Prometheus/Grafana observability. -
OpenTelemetry tracing. - Horizontal scaling experiments. - Schema
evolution and event compatibility. - Kubernetes deployment. - OAuth/OIDC
integration. - Admin audit reports. - Dead-letter replay tooling. -
Circuit breakers for external provider handlers.

These are future work, not commitments for the initial seven-day
deadline.

======================================================================
28. GLOSSARY
======================================================================

Acknowledgement (ACK): Broker signal that a delivered message has been
handled according to the consumer’s policy.

Attempt: A single recorded execution of a job.

Backoff: Waiting progressively longer between retries.

Broker: Messaging infrastructure such as RabbitMQ or Kafka.

Consumer group: A set of Kafka consumers coordinating work across
partitions.

Correlation ID: Identifier used to connect logs and events across
components.

Dead-letter queue: Place for messages that cannot be processed under
configured rules.

Idempotency: Repeating the same logical operation does not create
additional unintended effects.

Lease: Time-limited claim on a resource, which requires careful
expiration and ownership handling.

Outbox pattern: Persist a domain change and the event to publish in one
database transaction, then publish asynchronously.

Partition: Kafka’s ordered log unit; ordering is guaranteed within a
partition, not globally.

Poison message: A message that repeatedly fails due to malformed data or
an unrecoverable condition.

RBAC: Role-based access control.

Retryable failure: Error that may succeed on a later attempt.

State machine: Explicit model of valid states and transitions.

Tenant isolation: Ensuring one customer/workspace cannot access
another’s data.

Worker heartbeat: Periodic signal that a worker is alive.

======================================================================
29. FINAL BUILD PRIORITIES
======================================================================

If time becomes tight, preserve these priorities in order:

1.  Reliable job submission and PostgreSQL persistence.
2.  Explicit state machine, attempts, and audit events.
3.  RabbitMQ worker execution with safe acknowledgement behavior.
4.  Idempotency and duplicate delivery handling.
5.  Retry policy, terminal failure, and basic recovery.
6.  JWT and authorization.
7.  Transactional outbox and a real Kafka consumer.
8.  Redis rate limiting/heartbeat.
9.  Functional dashboard.
10. Delayed jobs and advanced scheduling.
11. Load testing, polished visuals, and stretch features.

Do not sacrifice core correctness just to say the project uses every
technology. A documented, tested implementation with honest limits is
the goal.

END OF SPECIFICATION
