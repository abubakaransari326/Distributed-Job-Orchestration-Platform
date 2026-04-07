package com.distributedjob.common;

/**
 * Lifecycle state of a job in persistence and processing.
 */
public enum JobStatus {

    /** Created and persisted; not yet handed to the message broker. */
    PENDING,

    /** Published to the job queue (e.g. Kafka) and waiting for a worker. */
    QUEUED,

    /** A worker has claimed the job and is executing it. */
    RUNNING,

    /** Finished successfully. */
    COMPLETED,

    /** Finished with an error or exhausted retries. */
    FAILED
}
