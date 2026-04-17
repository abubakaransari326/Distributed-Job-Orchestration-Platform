package com.distributedjob.api.job;

import com.distributedjob.api.job.dto.CreateJobRequest;
import com.distributedjob.api.job.dto.JobCallbackRequest;
import com.distributedjob.api.job.dto.JobResponse;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/jobs")
public class JobController {

    private final JobService jobService;

    public JobController(JobService jobService) {
        this.jobService = jobService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public JobResponse createJob(@Valid @RequestBody CreateJobRequest request) {
        return jobService.createJob(request);
    }

    @GetMapping
    public Page<JobResponse> listJobs(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String type,
            @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable
    ) {
        return jobService.listJobs(status, type, pageable);
    }

    @GetMapping("/{id}")
    public JobResponse getJob(@PathVariable UUID id) {
        return jobService.getJob(id);
    }

    @PostMapping("/{id}/retry")
    public JobResponse retryJob(@PathVariable UUID id) {
        return jobService.retryJob(id);
    }

    @PostMapping("/callback")
    public JobResponse jobCallback(
            @Valid @RequestBody JobCallbackRequest request,
            @RequestHeader(value = "X-Callback-Secret", required = false) String callbackSecret
    ) {
        return jobService.handleJobCallback(request, callbackSecret);
    }
}
