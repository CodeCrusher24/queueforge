package com.queueforge.job;

public enum JobStatus {
    SCHEDULED,
    QUEUED,
    RUNNING,
    RETRY_WAIT,
    SUCCEEDED,
    FAILED,
    CANCELLED,
    DEAD_LETTERED
}