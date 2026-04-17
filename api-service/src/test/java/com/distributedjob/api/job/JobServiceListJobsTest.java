package com.distributedjob.api.job;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.distributedjob.api.config.JobProperties;
import com.distributedjob.api.job.dto.JobResponse;
import com.distributedjob.api.kafka.JobMessagePublisher;
import com.distributedjob.common.JobStatus;
import com.distributedjob.common.JobType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

@ExtendWith(MockitoExtension.class)
class JobServiceListJobsTest {

    @Mock
    private JobRepository jobRepository;

    @Mock
    private JobMessagePublisher jobMessagePublisher;

    private JobService jobService;

    @BeforeEach
    void setUp() {
        jobService = new JobService(jobRepository, jobMessagePublisher, jobProperties(3), "");
    }

    @Test
    void listJobs_capsPageSizeAt100() {
        Pageable requested = PageRequest.of(0, 500);
        when(jobRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenAnswer(invocation -> {
                    Pageable actual = invocation.getArgument(1);
                    return new PageImpl<JobEntity>(List.of(), actual, 0);
                });

        jobService.listJobs(null, null, requested);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(jobRepository).findAll(any(Specification.class), pageableCaptor.capture());
        assertEquals(JobService.MAX_LIST_PAGE_SIZE, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void listJobs_invalidStatus_throwsBadRequest() {
        assertThrows(ResponseStatusException.class, () -> jobService.listJobs("not-a-status", null, PageRequest.of(0, 10)));
    }

    @Test
    void listJobs_invalidType_throwsBadRequest() {
        assertThrows(ResponseStatusException.class, () -> jobService.listJobs(null, "FAX", PageRequest.of(0, 10)));
    }

    @Test
    void listJobs_mapsEntitiesToResponses() {
        UUID id = UUID.randomUUID();
        JobEntity entity = new JobEntity();
        entity.setId(id);
        entity.setType(JobType.EMAIL);
        entity.setStatus(JobStatus.FAILED);
        entity.setPayloadJson("{}");
        entity.setRetries(2);
        Instant t = Instant.parse("2025-01-01T00:00:00Z");
        ReflectionTestUtils.setField(entity, "createdAt", t);
        ReflectionTestUtils.setField(entity, "updatedAt", t);

        when(jobRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(entity), PageRequest.of(0, 20), 1));

        Page<JobResponse> page = jobService.listJobs("failed", "email", PageRequest.of(0, 20));

        assertEquals(1, page.getTotalElements());
        JobResponse r = page.getContent().get(0);
        assertEquals(id, r.id());
        assertEquals(JobType.EMAIL, r.type());
        assertEquals(JobStatus.FAILED, r.status());
        assertEquals(2, r.retries());
    }

    private static JobProperties jobProperties(int retryMax) {
        JobProperties props = new JobProperties();
        props.getRetry().setMax(retryMax);
        return props;
    }
}
