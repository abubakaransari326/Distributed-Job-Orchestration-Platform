package com.distributedjob.api.job;

import com.distributedjob.common.JobStatus;
import com.distributedjob.common.JobType;
import jakarta.persistence.criteria.Predicate;
import java.util.ArrayList;
import java.util.List;
import org.springframework.data.jpa.domain.Specification;

final class JobSpecifications {

    private JobSpecifications() {
    }

    static Specification<JobEntity> withOptionalFilters(JobStatus status, JobType type) {
        return (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (status != null) {
                predicates.add(cb.equal(root.get("status"), status));
            }
            if (type != null) {
                predicates.add(cb.equal(root.get("type"), type));
            }
            if (predicates.isEmpty()) {
                return cb.conjunction();
            }
            return cb.and(predicates.toArray(Predicate[]::new));
        };
    }
}
