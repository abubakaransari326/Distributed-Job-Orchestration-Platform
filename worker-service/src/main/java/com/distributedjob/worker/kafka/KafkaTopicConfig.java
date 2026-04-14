package com.distributedjob.worker.kafka;

import com.distributedjob.common.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Ensures topics exist when the worker starts before or without the API (idempotent with API's beans).
 */
@Configuration
public class KafkaTopicConfig {

    @Bean
    public NewTopic jobsTopic() {
        return TopicBuilder.name(KafkaTopics.JOBS)
                .partitions(1)
                .replicas(1)
                .build();
    }

    @Bean
    public NewTopic jobsDlqTopic() {
        return TopicBuilder.name(KafkaTopics.JOBS_DLQ)
                .partitions(1)
                .replicas(1)
                .build();
    }
}
