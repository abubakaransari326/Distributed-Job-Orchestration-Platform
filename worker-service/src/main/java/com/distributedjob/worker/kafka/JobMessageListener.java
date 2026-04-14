package com.distributedjob.worker.kafka;

import com.distributedjob.common.JobMessage;
import com.distributedjob.common.KafkaTopics;
import com.distributedjob.worker.job.JobExecutionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class JobMessageListener {

    private static final Logger log = LoggerFactory.getLogger(JobMessageListener.class);

    private final JobExecutionService jobExecutionService;

    public JobMessageListener(JobExecutionService jobExecutionService) {
        this.jobExecutionService = jobExecutionService;
    }

    @KafkaListener(
            topics = KafkaTopics.JOBS,
            groupId = "${spring.kafka.consumer.group-id}"
    )
    public void onMessage(JobMessage message) {
        log.debug("Received job message jobId={} type={}", message.jobId(), message.type());
        jobExecutionService.process(message);
    }
}
