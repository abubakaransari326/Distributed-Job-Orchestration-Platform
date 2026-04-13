package com.distributedjob.api.job.dto;

import com.distributedjob.common.JobStatus;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;

/**
 * Body for {@code POST /jobs/callback}. External systems report a terminal outcome for an
 * {@code EXTERNAL} job that is already in progress ({@link JobStatus#RUNNING}).
 */
public record JobCallbackRequest(
        @NotNull UUID jobId,
        @NotNull JobStatus status,
        String detail
) {
}
