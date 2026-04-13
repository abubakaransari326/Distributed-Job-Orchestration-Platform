package com.distributedjob.api.kafka;

import com.distributedjob.common.KafkaTopics;
import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

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
