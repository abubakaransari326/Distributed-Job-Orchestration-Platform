package com.distributedjob.common;

import java.util.UUID;

/**
 * Payload published to Kafka for a job. Kept small and JSON-friendly so both
 * services serialize it the same way (typically as JSON).
 *
 * @param jobId       id of the persisted job row (same id clients use in REST URLs)
 * @param type        which handler should run
 * @param payloadJson job-specific data as a JSON object string (e.g. {@code {"to":"a@b.com"}})
 */
public record JobMessage(UUID jobId, JobType type, String payloadJson) {
}
