package com.distributedjob.api.job.dto;

import com.distributedjob.common.JobStatus;
import com.distributedjob.common.JobType;
import java.time.Instant;
import java.util.UUID;

public record JobResponse(
        UUID id,
        JobType type,
        JobStatus status,
        String payloadJson,
        int retries,
        Instant createdAt,
        Instant updatedAt
) {
}
