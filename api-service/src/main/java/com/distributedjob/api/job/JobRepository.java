package com.distributedjob.api.job;

import com.distributedjob.common.JobStatus;
import com.distributedjob.common.JobType;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface JobRepository extends JpaRepository<JobEntity, UUID>, JpaSpecificationExecutor<JobEntity> {
    List<JobEntity> findTop100ByTypeAndStatusAndRunningStartedAtBeforeOrderByRunningStartedAtAsc(
            JobType type,
            JobStatus status,
            Instant runningStartedAtBefore
    );
}
