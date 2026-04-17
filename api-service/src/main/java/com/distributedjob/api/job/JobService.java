package com.distributedjob.api.job;

import com.distributedjob.api.kafka.JobMessagePublisher;
import com.distributedjob.api.config.JobProperties;
import com.distributedjob.api.job.dto.CreateJobRequest;
import com.distributedjob.api.job.dto.JobCallbackRequest;
import com.distributedjob.api.job.dto.JobResponse;
import com.distributedjob.common.JobDlqMessage;
import com.distributedjob.common.JobMessage;
import com.distributedjob.common.JobStatus;
import com.distributedjob.common.JobType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.BAD_REQUEST;
import static org.springframework.http.HttpStatus.CONFLICT;
import static org.springframework.http.HttpStatus.NOT_FOUND;
import static org.springframework.http.HttpStatus.UNAUTHORIZED;

@Service
public class JobService {

    private static final Logger log = LoggerFactory.getLogger(JobService.class);

    /** Upper bound for {@code GET /jobs} page size. */
    static final int MAX_LIST_PAGE_SIZE = 100;

    private final JobRepository jobRepository;
    private final JobMessagePublisher jobMessagePublisher;
    private final String callbackSecret;
    private final int retryMax;
    private final java.time.Duration externalRunningTimeout;

    public JobService(
            JobRepository jobRepository,
            JobMessagePublisher jobMessagePublisher,
            JobProperties jobProperties,
            @Value("${job.callback.secret:}") String callbackSecret
    ) {
        this.jobRepository = jobRepository;
        this.jobMessagePublisher = jobMessagePublisher;
        this.callbackSecret = callbackSecret == null ? "" : callbackSecret;
        this.retryMax = Math.max(0, jobProperties.getRetry().getMax());
        this.externalRunningTimeout = jobProperties.getExternal().getRunningTimeout();
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

        // Persist QUEUED before publishing so workers never observe PENDING for an already-enqueued message.
        saved.setStatus(JobStatus.QUEUED);
        saved = jobRepository.save(saved);
        log.info("Job marked QUEUED before Kafka publish jobId={}", saved.getId());

        JobMessage message = new JobMessage(saved.getId(), saved.getType(), saved.getPayloadJson());
        jobMessagePublisher.publish(message);
        log.info("Job message published jobId={} status={}", saved.getId(), saved.getStatus());

        return toResponse(saved);
    }

    /**
     * List jobs with optional filters and paging. Unknown {@code status} / {@code type} strings yield 400.
     */
    public Page<JobResponse> listJobs(String statusParam, String typeParam, Pageable pageable) {
        JobStatus statusFilter = parseStatus(statusParam);
        JobType typeFilter = parseType(typeParam);
        Pageable capped = capPageSize(pageable);
        Specification<JobEntity> spec = JobSpecifications.withOptionalFilters(statusFilter, typeFilter);
        return jobRepository.findAll(spec, capped).map(this::toResponse);
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
        if (entity.getRetries() >= retryMax) {
            log.warn("Retry rejected jobId={} retries={} max={}", id, entity.getRetries(), retryMax);
            throw new ResponseStatusException(
                    CONFLICT,
                    "Retry limit reached (" + retryMax + ") for job " + id
            );
        }

        log.info("Retry requested jobId={} retriesBefore={}", id, entity.getRetries());
        entity.setStatus(JobStatus.QUEUED);
        entity.setRetries(entity.getRetries() + 1);
        entity.setRunningStartedAt(null);
        JobEntity saved = jobRepository.save(entity);
        log.info("Job marked QUEUED before Kafka publish jobId={} retries={}", saved.getId(), saved.getRetries());

        JobMessage message = new JobMessage(saved.getId(), saved.getType(), saved.getPayloadJson());
        jobMessagePublisher.publish(message);
        log.info("Job re-queued after retry jobId={} retries={} status={}", saved.getId(), saved.getRetries(), saved.getStatus());

        return toResponse(saved);
    }

    /**
     * Reconcile EXTERNAL jobs stuck in RUNNING longer than configured timeout.
     * <ul>
     *   <li>If retries &lt; retry.max: re-queue and increment retries.</li>
     *   <li>If retries &gt;= retry.max: mark FAILED (terminal).</li>
     * </ul>
     *
     * @return number of jobs updated.
     */
    @Transactional
    public int processExternalTimeouts(Instant now) {
        Instant cutoff = now.minus(externalRunningTimeout);
        List<JobEntity> stuck = jobRepository.findTop100ByTypeAndStatusAndRunningStartedAtBeforeOrderByRunningStartedAtAsc(
                JobType.EXTERNAL,
                JobStatus.RUNNING,
                cutoff
        );
        if (stuck.isEmpty()) {
            return 0;
        }

        int updated = 0;
        for (JobEntity job : stuck) {
            if (job.getRetries() >= retryMax) {
                job.setStatus(JobStatus.FAILED);
                job.setRunningStartedAt(null);
                JobEntity saved = jobRepository.save(job);
                jobMessagePublisher.publishDlq(new JobDlqMessage(
                        saved.getId(),
                        saved.getType(),
                        saved.getRetries(),
                        "RETRY_LIMIT_EXHAUSTED"
                ));
                log.warn(
                        "External timeout terminal failure jobId={} retries={} max={} cutoff={}",
                        saved.getId(),
                        saved.getRetries(),
                        retryMax,
                        cutoff
                );
                updated++;
                continue;
            }

            int newRetries = job.getRetries() + 1;
            job.setStatus(JobStatus.QUEUED);
            job.setRetries(newRetries);
            job.setRunningStartedAt(null);
            JobEntity saved = jobRepository.save(job);
            JobMessage message = new JobMessage(saved.getId(), saved.getType(), saved.getPayloadJson());
            jobMessagePublisher.publish(message);
            log.info(
                    "External timeout re-queued jobId={} retries={} cutoff={}",
                    saved.getId(),
                    saved.getRetries(),
                    cutoff
            );
            updated++;
        }
        return updated;
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
        entity.setRunningStartedAt(null);
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

    private static JobStatus parseStatus(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JobStatus.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid status filter; use a JobStatus name such as FAILED or QUEUED"
            );
        }
    }

    private static JobType parseType(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return JobType.valueOf(raw.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(
                    BAD_REQUEST,
                    "Invalid type filter; use a JobType name such as EMAIL or EXTERNAL"
            );
        }
    }

    private static Pageable capPageSize(Pageable pageable) {
        int size = pageable.getPageSize();
        if (size > MAX_LIST_PAGE_SIZE) {
            return PageRequest.of(pageable.getPageNumber(), MAX_LIST_PAGE_SIZE, pageable.getSort());
        }
        return pageable;
    }
}
