package com.distributedjob.api.job;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.distributedjob.api.job.dto.JobResponse;
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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableHandlerMethodArgumentResolver;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

@ExtendWith(MockitoExtension.class)
class JobControllerListJobsTest {

    @Mock
    private JobService jobService;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        PageableHandlerMethodArgumentResolver pageableResolver = new PageableHandlerMethodArgumentResolver();
        pageableResolver.setFallbackPageable(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")));
        mockMvc = MockMvcBuilders.standaloneSetup(new JobController(jobService))
                .setCustomArgumentResolvers(pageableResolver)
                .build();
    }

    @Test
    void listJobs_returnsPagedJson() throws Exception {
        UUID id = UUID.fromString("11111111-1111-1111-1111-111111111111");
        Instant t = Instant.parse("2025-06-01T12:00:00Z");
        JobResponse row = new JobResponse(id, JobType.EMAIL, JobStatus.FAILED, "{}", 1, t, t);
        when(jobService.listJobs(eq("FAILED"), eq("EMAIL"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(row), PageRequest.of(0, 20), 42));

        mockMvc.perform(get("/jobs").param("status", "FAILED").param("type", "EMAIL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(42))
                .andExpect(jsonPath("$.content[0].id").value(id.toString()))
                .andExpect(jsonPath("$.content[0].status").value("FAILED"))
                .andExpect(jsonPath("$.content[0].type").value("EMAIL"));
    }

    @Test
    void listJobs_passesNullFiltersWhenOmitted() throws Exception {
        when(jobService.listJobs(isNull(), isNull(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));

        mockMvc.perform(get("/jobs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(0));
    }
}
