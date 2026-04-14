package com.distributedjob.worker.job;

import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface JobRepository extends JpaRepository<JobEntity, UUID> {
}
