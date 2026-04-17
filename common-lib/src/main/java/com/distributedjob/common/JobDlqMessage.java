package com.distributedjob.common;

import java.util.UUID;

/**
 * Minimal payload for terminal job failures sent to the DLQ topic.
 *
 * @param jobId id of the terminally failed job
 * @param type job type
 * @param retries retries consumed when dead-lettered
 * @param reason short reason code (for example RETRY_LIMIT_EXHAUSTED)
 */
public record JobDlqMessage(UUID jobId, JobType type, int retries, String reason) {
}
