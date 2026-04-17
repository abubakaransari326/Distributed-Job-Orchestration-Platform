package com.distributedjob.api.config;

import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Platform job policy (retry budget, EXTERNAL watchdog, scheduler cadence).
 * <p>
 * {@code retries} on each row counts recovery actions already applied (manual retry and,
 * when implemented, timeout-driven requeue each increment the counter). No further manual
 * retry or timeout requeue when {@code retries >= retry.max}.
 * <p>
 * {@code job.callback.secret} stays in YAML for {@link com.distributedjob.api.job.JobService}
 * {@code @Value} injection and is not bound here.
 */
@ConfigurationProperties(prefix = "job")
public class JobProperties {

    private final Retry retry = new Retry();
    private final External external = new External();
    private final Watchdog watchdog = new Watchdog();

    public Retry getRetry() {
        return retry;
    }

    public External getExternal() {
        return external;
    }

    public Watchdog getWatchdog() {
        return watchdog;
    }

    public static class Retry {
        /**
         * When {@code jobs.retries >= max}, the job must not be manually retried or timeout-requeued.
         */
        private int max = 3;

        public int getMax() {
            return max;
        }

        public void setMax(int max) {
            this.max = max;
        }
    }

    public static class External {
        /**
         * How long an EXTERNAL job may stay RUNNING before the watchdog considers it stuck.
         */
        private Duration runningTimeout = Duration.ofMinutes(30);

        public Duration getRunningTimeout() {
            return runningTimeout;
        }

        public void setRunningTimeout(Duration runningTimeout) {
            this.runningTimeout = runningTimeout == null ? Duration.ofMinutes(30) : runningTimeout;
        }
    }

    public static class Watchdog {
        /**
         * Interval between EXTERNAL RUNNING timeout scans (when the scheduler is enabled).
         */
        private Duration fixedDelay = Duration.ofMinutes(1);

        public Duration getFixedDelay() {
            return fixedDelay;
        }

        public void setFixedDelay(Duration fixedDelay) {
            this.fixedDelay = fixedDelay == null ? Duration.ofMinutes(1) : fixedDelay;
        }
    }
}
