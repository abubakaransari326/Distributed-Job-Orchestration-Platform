package com.distributedjob.api.job;

import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
class ExternalJobWatchdog {

    private static final Logger log = LoggerFactory.getLogger(ExternalJobWatchdog.class);

    private final JobService jobService;

    ExternalJobWatchdog(JobService jobService) {
        this.jobService = jobService;
    }

    @Scheduled(fixedDelayString = "${job.watchdog.fixed-delay:60000}")
    void run() {
        int updated = jobService.processExternalTimeouts(Instant.now());
        if (updated > 0) {
            log.info("External watchdog processed {} timed-out RUNNING jobs", updated);
        }
    }
}
