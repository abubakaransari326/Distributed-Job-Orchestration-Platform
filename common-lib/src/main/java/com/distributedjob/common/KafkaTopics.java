package com.distributedjob.common;

/**
 * Central place for Kafka topic names shared by the API (producer) and worker (consumer).
 */
public final class KafkaTopics {

    /** Primary stream of jobs to execute. */
    public static final String JOBS = "jobs";

    /** Jobs that failed after retry policy; for inspection and manual handling. */
    public static final String JOBS_DLQ = "jobs-dlq";

    private KafkaTopics() {
    }
}
