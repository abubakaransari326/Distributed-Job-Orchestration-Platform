package com.distributedjob.api.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

class JobPropertiesTest {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(TestConfig.class)
            .withPropertyValues(
                    "job.retry.max=5",
                    "job.external.running-timeout=15m",
                    "job.watchdog.fixed-delay=90s"
            );

    @Test
    void bindsFromEnvironment() {
        runner.run(ctx -> {
            JobProperties props = ctx.getBean(JobProperties.class);
            assertThat(props.getRetry().getMax()).isEqualTo(5);
            assertThat(props.getExternal().getRunningTimeout()).isEqualTo(Duration.ofMinutes(15));
            assertThat(props.getWatchdog().getFixedDelay()).isEqualTo(Duration.ofSeconds(90));
        });
    }

    @Configuration
    @EnableConfigurationProperties(JobProperties.class)
    static class TestConfig {
    }
}
