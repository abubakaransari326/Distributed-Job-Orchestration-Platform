package com.distributedjob.api.job;

import com.distributedjob.api.kafka.JobProducer;
import com.distributedjob.api.job.dto.CreateJobRequest;
import com.distributedjob.api.job.dto.JobCallbackRequest;
import com.distributedjob.api.job.dto.JobResponse;
import com.distributedjob.common.JobMessage;
import com.distributedjob.common.JobStatus;
import com.distributedjob.common.JobType;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    private final JobRepository jobRepository;
    private final JobProducer jobProducer;
    private final String callbackSecret;

    public JobService(
            JobRepository jobRepository,
            JobProducer jobProducer,
            @Value("${job.callback.secret:}") String callbackSecret
    ) {
        this.jobRepository = jobRepository;
        this.jobProducer = jobProducer;
        this.callbackSecret = callbackSecret == null ? "" : callbackSecret;
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
        log.info("Job created jobId={} type={} status={}", saved.getId(), saved.getType(), saved.getStatus());

        JobMessage message = new JobMessage(saved.getId(), saved.getType(), saved.getPayloadJson());
        jobProducer.publish(message);

        saved.setStatus(JobStatus.QUEUED);
        saved = jobRepository.save(saved);
        log.info("Job enqueued to Kafka jobId={} status={}", saved.getId(), saved.getStatus());

        return toResponse(saved);
    }

    public JobResponse getJob(UUID id) {
        JobEntity entity = jobRepository.findById(id)
                .orElseThrow(() -> {
                    log.debug("Job not found jobId={}", id);
                    return new ResponseStatusException(NOT_FOUND, "Job not found: " + id);
                });
        log.debug("Job loaded jobId={} status={} type={}", entity.getId(), entity.getStatus(), entity.getType());
        return toResponse(entity);
    }

    /**
     * Re-enqueue a failed job so a worker can process it again.
     * Only {@link JobStatus#FAILED} jobs are eligible.
     */
    public JobResponse retryJob(UUID id) {
        JobEntity entity = jobRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Job not found: " + id));

        if (entity.getStatus() != JobStatus.FAILED) {
            log.warn("Retry rejected jobId={} status={}", id, entity.getStatus());
            throw new ResponseStatusException(
                    CONFLICT,
                    "Only FAILED jobs can be retried; current status is " + entity.getStatus()
            );
        }

        log.info("Retry requested jobId={} retriesBefore={}", id, entity.getRetries());
        JobMessage message = new JobMessage(entity.getId(), entity.getType(), entity.getPayloadJson());
        jobProducer.publish(message);

        entity.setStatus(JobStatus.QUEUED);
        entity.setRetries(entity.getRetries() + 1);
        JobEntity saved = jobRepository.save(entity);
        log.info("Job re-queued after retry jobId={} retries={} status={}", saved.getId(), saved.getRetries(), saved.getStatus());

        return toResponse(saved);
    }

    /**
     * External partner reports completion for an {@link JobType#EXTERNAL} job.
     * Job must be {@link JobStatus#RUNNING} (set by the worker after it starts external work).
     * When {@code job.callback.secret} is non-blank, header {@code X-Callback-Secret} must match.
     */
    public JobResponse handleJobCallback(JobCallbackRequest request, String callbackSecretHeader) {
        if (request == null) {
            throw new ResponseStatusException(BAD_REQUEST, "Request body is required");
        }

        if (!callbackSecret.isBlank()) {
            if (callbackSecretHeader == null || !constantTimeEquals(callbackSecret, callbackSecretHeader)) {
                log.warn("Callback rejected: invalid or missing X-Callback-Secret jobId={}", request.jobId());
                throw new ResponseStatusException(UNAUTHORIZED, "Invalid or missing X-Callback-Secret");
            }
        }

        if (request.status() != JobStatus.COMPLETED && request.status() != JobStatus.FAILED) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "status must be COMPLETED or FAILED, got: " + request.status()
            );
        }

        JobEntity entity = jobRepository.findById(request.jobId())
                .orElseThrow(() -> new ResponseStatusException(NOT_FOUND, "Job not found: " + request.jobId()));

        if (entity.getType() != JobType.EXTERNAL) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Callbacks are only accepted for EXTERNAL jobs; type is " + entity.getType()
            );
        }

        if (entity.getStatus() != JobStatus.RUNNING) {
            throw new ResponseStatusException(
                    CONFLICT,
                    "Callback only allowed when job is RUNNING; current status is " + entity.getStatus()
            );
        }

        entity.setStatus(request.status());
        JobEntity saved = jobRepository.save(entity);
        log.info(
                "Callback applied jobId={} terminalStatus={} detailPresent={}",
                saved.getId(),
                saved.getStatus(),
                request.detail() != null && !request.detail().isBlank()
        );
        return toResponse(saved);
    }

    private static boolean constantTimeEquals(String expected, String provided) {
        byte[] a = expected.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        byte[] b = provided.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        return a.length == b.length && java.security.MessageDigest.isEqual(a, b);
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
