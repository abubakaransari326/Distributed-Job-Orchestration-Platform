package com.distributedjob.api.kafka;

import com.distributedjob.common.JobDlqMessage;
import com.distributedjob.common.KafkaTopics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class JobDlqListener {

    private static final Logger log = LoggerFactory.getLogger(JobDlqListener.class);

    @KafkaListener(topics = KafkaTopics.JOBS_DLQ, groupId = "api-dlq-audit")
    public void onDlq(JobDlqMessage message) {
        log.warn(
                "DLQ message received jobId={} type={} retries={} reason={}",
                message.jobId(),
                message.type(),
                message.retries(),
                message.reason()
        );
    }
}
