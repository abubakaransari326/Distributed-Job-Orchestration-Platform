package com.distributedjob.api.job.dto;

import com.distributedjob.common.JobType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record CreateJobRequest(
        @NotNull JobType type,
        @NotBlank String payloadJson
) {
}
