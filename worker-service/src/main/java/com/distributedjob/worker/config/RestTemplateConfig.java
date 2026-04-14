package com.distributedjob.worker.config;

import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.client.RestTemplate;

@Configuration
public class RestTemplateConfig {

    @Bean
    public RestTemplate workerRestTemplate(
            RestTemplateBuilder builder,
            @Value("${job.worker.webhook.connect-timeout-ms:5000}") long connectTimeoutMs,
            @Value("${job.worker.webhook.read-timeout-ms:15000}") long readTimeoutMs
    ) {
        return builder
                .setConnectTimeout(Duration.ofMillis(connectTimeoutMs))
                .setReadTimeout(Duration.ofMillis(readTimeoutMs))
                .build();
    }
}
