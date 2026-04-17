package com.distributedjob.api.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distributedjob.api.config.JobProperties;
import com.distributedjob.api.kafka.JobMessagePublisher;
import com.distributedjob.api.job.dto.JobResponse;
import com.distributedjob.common.JobStatus;
import com.distributedjob.common.JobType;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class JobServiceRetryPolicyTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobMessagePublisher jobMessagePublisher;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        JobProperties props = new JobProperties();
        props.getRetry().setMax(3);
        jobService = new JobService(jobRepository, jobMessagePublisher, props, "");
    }

    @Test
    void retryJob_requeuesWhenUnderRetryLimit() {
        UUID id = UUID.randomUUID();
        JobEntity entity = baseFailedJob(id, 2);
        when(jobRepository.findById(id)).thenReturn(Optional.of(entity));
        when(jobRepository.save(entity)).thenReturn(entity);

        JobResponse response = jobService.retryJob(id);

        assertEquals(JobStatus.QUEUED, response.status());
        assertEquals(3, response.retries());
        verify(jobRepository).save(entity);
        verify(jobMessagePublisher).publish(any());
    }

    @Test
    void retryJob_rejectsWhenRetryLimitReached() {
        UUID id = UUID.randomUUID();
        JobEntity entity = baseFailedJob(id, 3);
        when(jobRepository.findById(id)).thenReturn(Optional.of(entity));

        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> jobService.retryJob(id));

        assertEquals(409, ex.getStatusCode().value());
        verify(jobRepository, never()).save(any());
        verify(jobMessagePublisher, never()).publish(any());
    }

    private static JobEntity baseFailedJob(UUID id, int retries) {
        JobEntity e = new JobEntity();
        e.setId(id);
        e.setType(JobType.EMAIL);
        e.setStatus(JobStatus.FAILED);
        e.setPayloadJson("{\"to\":\"x@y.com\"}");
        e.setRetries(retries);
        return e;
    }
}
