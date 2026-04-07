package com.distributedjob.api.kafka;

import com.distributedjob.common.JobMessage;
import com.distributedjob.common.KafkaTopics;
import java.time.Duration;
import java.util.concurrent.TimeUnit;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import static org.springframework.http.HttpStatus.SERVICE_UNAVAILABLE;

@Component
public class JobProducer {

    private static final long SEND_TIMEOUT_SECONDS = Duration.ofSeconds(5).toSeconds();

    private final KafkaTemplate<String, JobMessage> kafkaTemplate;

    public JobProducer(KafkaTemplate<String, JobMessage> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    public void publish(JobMessage message) {
        try {
            kafkaTemplate.send(KafkaTopics.JOBS, message.jobId().toString(), message)
                    .get(SEND_TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception ex) {
            throw new ResponseStatusException(
                    SERVICE_UNAVAILABLE,
                    "Failed to enqueue job to Kafka topic '" + KafkaTopics.JOBS + "'",
                    ex
            );
        }
    }
}
