package com.distributedjob.api.kafka;

import com.distributedjob.common.JobMessage;
import com.distributedjob.common.JobDlqMessage;

/**
 * Abstraction for enqueueing jobs to Kafka (implemented by {@link JobProducer}).
 */
public interface JobMessagePublisher {

    void publish(JobMessage message);

    void publishDlq(JobDlqMessage message);
}
