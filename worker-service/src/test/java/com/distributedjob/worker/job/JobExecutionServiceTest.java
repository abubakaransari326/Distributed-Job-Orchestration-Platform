package com.distributedjob.worker.job;

import com.distributedjob.common.JobMessage;
import com.distributedjob.common.JobStatus;
import com.distributedjob.common.JobType;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.ExpectedCount;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestTemplate;
import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

@ExtendWith(MockitoExtension.class)
class JobExecutionServiceTest {

    @Mock
    private JobRepository jobRepository;

    private ObjectMapper objectMapper;
    private JobExecutionService service;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        service = new JobExecutionService(jobRepository, objectMapper, new RestTemplate());
    }

    @Test
    void process_skipsWhenStatusNotQueued() {
        UUID id = UUID.randomUUID();
        JobEntity job = entity(id, JobType.EMAIL, JobStatus.RUNNING, "{\"to\":\"a@b.com\"}");
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        service.process(new JobMessage(id, JobType.EMAIL, job.getPayloadJson()));

        verify(jobRepository, never()).save(any());
    }

    @Test
    void process_email_completesWhenValid() {
        UUID id = UUID.randomUUID();
        JobEntity job = entity(id, JobType.EMAIL, JobStatus.QUEUED, "{\"to\":\"a@b.com\"}");
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        service.process(new JobMessage(id, JobType.EMAIL, job.getPayloadJson()));

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        verify(jobRepository, times(2)).save(job);
    }

    @Test
    void process_email_failsWhenToMissing() {
        UUID id = UUID.randomUUID();
        JobEntity job = entity(id, JobType.EMAIL, JobStatus.QUEUED, "{\"to\":\"\"}");
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        service.process(new JobMessage(id, JobType.EMAIL, job.getPayloadJson()));

        assertEquals(JobStatus.FAILED, job.getStatus());
        verify(jobRepository, times(2)).save(job);
    }

    @Test
    void process_report_completes() {
        UUID id = UUID.randomUUID();
        JobEntity job = entity(id, JobType.REPORT, JobStatus.QUEUED, "{}");
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        service.process(new JobMessage(id, JobType.REPORT, job.getPayloadJson()));

        assertEquals(JobStatus.COMPLETED, job.getStatus());
        verify(jobRepository, times(2)).save(job);
    }

    @Test
    void process_external_webhook2xx_staysRunning() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(ExpectedCount.once(), requestTo("http://partner.test/hook"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withSuccess("ok", MediaType.TEXT_PLAIN));

        UUID id = UUID.randomUUID();
        JobEntity job = entity(id, JobType.EXTERNAL, JobStatus.QUEUED, "{\"webhookUrl\":\"http://partner.test/hook\"}");
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        JobExecutionService svc = new JobExecutionService(jobRepository, objectMapper, rt);
        svc.process(new JobMessage(id, JobType.EXTERNAL, job.getPayloadJson()));

        assertEquals(JobStatus.RUNNING, job.getStatus());
        verify(jobRepository, times(2)).save(job);
        server.verify();
    }

    @Test
    void process_external_webhook5xx_fails() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(ExpectedCount.once(), requestTo("http://partner.test/hook"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(withStatus(HttpStatus.BAD_GATEWAY).body("err"));

        UUID id = UUID.randomUUID();
        JobEntity job = entity(id, JobType.EXTERNAL, JobStatus.QUEUED, "{\"webhookUrl\":\"http://partner.test/hook\"}");
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        JobExecutionService svc = new JobExecutionService(jobRepository, objectMapper, rt);
        svc.process(new JobMessage(id, JobType.EXTERNAL, job.getPayloadJson()));

        assertEquals(JobStatus.FAILED, job.getStatus());
        verify(jobRepository, times(2)).save(job);
        server.verify();
    }

    @Test
    void process_external_restException_fails() {
        RestTemplate rt = new RestTemplate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(rt).build();
        server.expect(ExpectedCount.once(), requestTo("http://partner.test/hook"))
                .andExpect(method(HttpMethod.POST))
                .andRespond(request -> {
                    throw new IOException("connection refused");
                });

        UUID id = UUID.randomUUID();
        JobEntity job = entity(id, JobType.EXTERNAL, JobStatus.QUEUED, "{\"webhookUrl\":\"http://partner.test/hook\"}");
        when(jobRepository.findById(id)).thenReturn(Optional.of(job));

        JobExecutionService svc = new JobExecutionService(jobRepository, objectMapper, rt);
        svc.process(new JobMessage(id, JobType.EXTERNAL, job.getPayloadJson()));

        assertEquals(JobStatus.FAILED, job.getStatus());
        verify(jobRepository, times(2)).save(job);
        server.verify();
    }

    private static JobEntity entity(UUID id, JobType type, JobStatus status, String payloadJson) {
        JobEntity job = new JobEntity();
        job.setId(id);
        job.setType(type);
        job.setStatus(status);
        job.setPayloadJson(payloadJson);
        job.setRetries(0);
        return job;
    }
}
