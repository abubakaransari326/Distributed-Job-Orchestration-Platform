package com.distributedjob.api.job;

import com.distributedjob.api.job.dto.CreateJobRequest;
import com.distributedjob.api.job.dto.JobResponse;
import com.distributedjob.common.JobStatus;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.NOT_FOUND;

@Service
public class JobService {

    private final JobRepository jobRepository;

    public JobService(JobRepository jobRepository) {
        this.jobRepository = jobRepository;
    }

    public JobResponse createJob(CreateJobRequest request) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }
        if (request.type() == null) {
            throw new ResponseStatusException(BAD_REQUEST, "type is required");
        }
        if (request.payloadJson() == null || request.payloadJson().isBlank()) {
            throw new ResponseStatusException(BAD_REQUEST, "payloadJson must not be blank");
        }

        JobEntity entity = new JobEntity();
        entity.setType(request.type());
        entity.setStatus(JobStatus.PENDING);
        entity.setPayloadJson(request.payloadJson());
        entity.setRetries(0);

        JobEntity saved = jobRepository.save(entity);
        return toResponse(saved);
    }

    public JobResponse getJob(UUID id) {
        JobEntity entity = jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Job not found: " + id));
        return toResponse(entity);
    }

    private JobResponse toResponse(JobEntity entity) {
        return new JobResponse(
                entity.getId(),
                entity.getType(),
                entity.getStatus(),
                entity.getPayloadJson(),
                entity.getRetries(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
