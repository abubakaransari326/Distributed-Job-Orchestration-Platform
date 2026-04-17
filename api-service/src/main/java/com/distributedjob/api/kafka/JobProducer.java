package com.distributedjob.api.kafka;

import com.distributedjob.common.JobDlqMessage;
import com.distributedjob.common.JobMessage;
import com.distributedjob.common.KafkaTopics;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class JobProducer implements JobMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(JobProducer.class);

    private static final long SEND_TIMEOUT_SECONDS = Duration.ofSeconds(5).toSeconds();

    private final KafkaTemplate<String, Object> kafkaTemplate;

    public JobProducer(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(JobMessage message) {
        try {
            kafkaTemplate.send(KafkaTopics.JOBS, message.jobId().toString(), message)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.info("Kafka publish ok topic={} jobId={} type={}", KafkaTopics.JOBS, message.jobId(), message.type());
        } catch (Exception ex) {
            log.error(
                    "Kafka publish failed topic={} jobId={} type={}",
                    KafkaTopics.JOBS,
                    message.jobId(),
                    message.type(),
                    ex
            );
            throw new ResponseStatusException(
                    SERVICE_UNAVAILABLE,
                    "Failed to enqueue job to Kafka topic '" + KafkaTopics.JOBS + "'",
                    ex
            );
        }
    }

    @Override
    public void publishDlq(JobDlqMessage message) {
        try {
            kafkaTemplate.send(KafkaTopics.JOBS_DLQ, message.jobId().toString(), message)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
            log.warn(
                    "Kafka DLQ publish ok topic={} jobId={} type={} retries={} reason={}",
                    KafkaTopics.JOBS_DLQ,
                    message.jobId(),
                    message.type(),
                    message.retries(),
                    message.reason()
            );
        } catch (Exception ex) {
            log.error(
                    "Kafka DLQ publish failed topic={} jobId={} type={}",
                    KafkaTopics.JOBS_DLQ,
                    message.jobId(),
                    message.type(),
                    ex
            );
            throw new ResponseStatusException(
                    SERVICE_UNAVAILABLE,
                    "Failed to enqueue DLQ message to Kafka topic '" + KafkaTopics.JOBS_DLQ + "'",
                    ex
            );
        }
    }
}
