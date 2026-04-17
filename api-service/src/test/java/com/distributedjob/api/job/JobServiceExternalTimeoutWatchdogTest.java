package com.distributedjob.api.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distributedjob.api.config.JobProperties;
import com.distributedjob.api.kafka.JobMessagePublisher;
import com.distributedjob.common.JobStatus;
import com.distributedjob.common.JobType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class JobServiceExternalTimeoutWatchdogTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobMessagePublisher jobMessagePublisher;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        JobProperties props = new JobProperties();
        props.getRetry().setMax(3);
        props.getExternal().setRunningTimeout(java.time.Duration.ofMinutes(30));
        jobService = new JobService(jobRepository, jobMessagePublisher, props, "");
    }

    @Test
    void processExternalTimeouts_requeuesWhenRetriesBelowLimit() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        JobEntity running = externalRunningJob(1);
        when(jobRepository.findTop100ByTypeAndStatusAndRunningStartedAtBeforeOrderByRunningStartedAtAsc(
                JobType.EXTERNAL, JobStatus.RUNNING, now.minusSeconds(1800)))
                .thenReturn(List.of(running));
        when(jobRepository.save(running)).thenReturn(running);

        int updated = jobService.processExternalTimeouts(now);

        assertEquals(1, updated);
        assertEquals(JobStatus.QUEUED, running.getStatus());
        assertEquals(2, running.getRetries());
        assertNull(running.getRunningStartedAt());
        verify(jobMessagePublisher).publish(any());
    }

    @Test
    void processExternalTimeouts_marksFailedWhenRetryBudgetExhausted() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        JobEntity running = externalRunningJob(3);
        when(jobRepository.findTop100ByTypeAndStatusAndRunningStartedAtBeforeOrderByRunningStartedAtAsc(
                JobType.EXTERNAL, JobStatus.RUNNING, now.minusSeconds(1800)))
                .thenReturn(List.of(running));
        when(jobRepository.save(running)).thenReturn(running);

        int updated = jobService.processExternalTimeouts(now);

        assertEquals(1, updated);
        assertEquals(JobStatus.FAILED, running.getStatus());
        assertEquals(3, running.getRetries());
        assertNull(running.getRunningStartedAt());
        verify(jobMessagePublisher, never()).publish(any());
        verify(jobMessagePublisher).publishDlq(any());
    }

    @Test
    void processExternalTimeouts_noopWhenNoTimedOutJobs() {
        Instant now = Instant.parse("2026-01-01T12:00:00Z");
        when(jobRepository.findTop100ByTypeAndStatusAndRunningStartedAtBeforeOrderByRunningStartedAtAsc(
                JobType.EXTERNAL, JobStatus.RUNNING, now.minusSeconds(1800)))
                .thenReturn(List.of());

        int updated = jobService.processExternalTimeouts(now);

        assertEquals(0, updated);
        verify(jobRepository, never()).save(any());
        verify(jobMessagePublisher, never()).publish(any());
        verify(jobMessagePublisher, never()).publishDlq(any());
    }

    private static JobEntity externalRunningJob(int retries) {
        JobEntity e = new JobEntity();
        e.setId(UUID.randomUUID());
        e.setType(JobType.EXTERNAL);
        e.setStatus(JobStatus.RUNNING);
        e.setRetries(retries);
        e.setPayloadJson("{\"webhookUrl\":\"https://httpbin.org/post\"}");
        e.setRunningStartedAt(Instant.parse("2026-01-01T11:00:00Z"));
        return e;
    }
}
