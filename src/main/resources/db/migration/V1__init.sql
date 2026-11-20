CREATE EXTENSION IF NOT EXISTS "pgcrypto";

CREATE TABLE users (
                       id UUID PRIMARY KEY,
                       email VARCHAR(255) NOT NULL UNIQUE,
                       password_hash VARCHAR(255) NOT NULL,
                       display_name VARCHAR(255) NOT NULL,
                       role VARCHAR(32) NOT NULL,
                       enabled BOOLEAN NOT NULL DEFAULT TRUE,
                       created_at TIMESTAMPTZ NOT NULL,
                       updated_at TIMESTAMPTZ NOT NULL
);

CREATE TABLE jobs (
                      id UUID PRIMARY KEY,
                      job_type VARCHAR(64) NOT NULL,
                      queue_name VARCHAR(64) NOT NULL DEFAULT 'default',
                      payload JSONB NOT NULL,
                      status VARCHAR(32) NOT NULL,
                      priority INTEGER NOT NULL DEFAULT 5,
                      idempotency_key VARCHAR(255),
                      request_hash VARCHAR(64),
                      run_at TIMESTAMPTZ NOT NULL,
                      created_by UUID NOT NULL REFERENCES users(id),
                      created_at TIMESTAMPTZ NOT NULL,
                      updated_at TIMESTAMPTZ NOT NULL,
                      started_at TIMESTAMPTZ,
                      finished_at TIMESTAMPTZ,
                      next_attempt_at TIMESTAMPTZ,
                      attempt_count INTEGER NOT NULL DEFAULT 0,
                      max_attempts INTEGER NOT NULL DEFAULT 3,
                      last_error_code VARCHAR(64),
                      last_error_message TEXT,
                      result JSONB,
                      version BIGINT NOT NULL DEFAULT 0,
                      correlation_id VARCHAR(64),
                      cancellation_requested BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE INDEX idx_jobs_status_created ON jobs (status, created_at DESC);
CREATE INDEX idx_jobs_status_next_attempt ON jobs (status, next_attempt_at);
CREATE INDEX idx_jobs_status_run_at ON jobs (status, run_at);
CREATE INDEX idx_jobs_queue_status_priority ON jobs (queue_name, status, priority DESC, created_at);
CREATE UNIQUE INDEX idx_jobs_idempotency ON jobs (idempotency_key) WHERE idempotency_key IS NOT NULL;

CREATE TABLE job_attempts (
                              id UUID PRIMARY KEY,
                              job_id UUID NOT NULL REFERENCES jobs(id),
                              attempt_number INTEGER NOT NULL,
                              worker_id VARCHAR(128),
                              status VARCHAR(32) NOT NULL,
                              started_at TIMESTAMPTZ NOT NULL,
                              finished_at TIMESTAMPTZ,
                              duration_ms BIGINT,
                              error_code VARCHAR(64),
                              error_message TEXT,
                              created_at TIMESTAMPTZ NOT NULL,
                              UNIQUE (job_id, attempt_number)
);

CREATE INDEX idx_attempts_job ON job_attempts (job_id, attempt_number);

CREATE TABLE job_events (
                            id UUID PRIMARY KEY,
                            job_id UUID NOT NULL REFERENCES jobs(id),
                            event_type VARCHAR(64) NOT NULL,
                            from_status VARCHAR(32),
                            to_status VARCHAR(32),
                            actor_type VARCHAR(16) NOT NULL,
                            actor_id VARCHAR(128),
                            details JSONB,
                            created_at TIMESTAMPTZ NOT NULL,
                            correlation_id VARCHAR(64)
);

CREATE INDEX idx_events_job_created ON job_events (job_id, created_at);

CREATE TABLE outbox_events (
                               id UUID PRIMARY KEY,
                               aggregate_type VARCHAR(32) NOT NULL,
                               aggregate_id UUID NOT NULL,
                               event_type VARCHAR(64) NOT NULL,
                               payload JSONB NOT NULL,
                               created_at TIMESTAMPTZ NOT NULL,
                               published_at TIMESTAMPTZ,
                               attempts INTEGER NOT NULL DEFAULT 0,
                               next_attempt_at TIMESTAMPTZ,
                               last_error TEXT,
                               status VARCHAR(16) NOT NULL DEFAULT 'PENDING'
);

CREATE INDEX idx_outbox_status_next_attempt ON outbox_events (status, next_attempt_at, created_at);
CREATE INDEX idx_outbox_aggregate ON outbox_events (aggregate_id, created_at);