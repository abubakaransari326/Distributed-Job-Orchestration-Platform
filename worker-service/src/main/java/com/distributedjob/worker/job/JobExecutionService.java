package com.distributedjob.worker.job;

import com.distributedjob.common.JobMessage;
import com.distributedjob.common.JobStatus;
import com.distributedjob.common.JobType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.time.Instant;
import java.util.Iterator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

@Service
public class JobExecutionService {

    private static final Logger log = LoggerFactory.getLogger(JobExecutionService.class);

    private final JobRepository jobRepository;
    private final ObjectMapper objectMapper;
    private final RestTemplate restTemplate;

    public JobExecutionService(
            JobRepository jobRepository,
            ObjectMapper objectMapper,
            RestTemplate restTemplate
    ) {
        this.jobRepository = jobRepository;
        this.objectMapper = objectMapper;
        this.restTemplate = restTemplate;
    }

    @Transactional
    public void process(JobMessage message) {
        JobEntity job = jobRepository.findById(message.jobId()).orElse(null);
        if (job == null) {
            log.warn("Job not found jobId={}", message.jobId());
            return;
        }

        if (job.getStatus() != JobStatus.QUEUED) {
            log.debug("Skip jobId={} status={} (expected QUEUED)", job.getId(), job.getStatus());
            return;
        }

        job.setStatus(JobStatus.RUNNING);
        job.setRunningStartedAt(Instant.now());
        jobRepository.save(job);
        log.info("Job claimed jobId={} type={}", job.getId(), job.getType());

        try {
            switch (job.getType()) {
                case EMAIL -> runEmail(job);
                case REPORT -> runReport(job);
                case EXTERNAL -> runExternal(job);
            }
        } catch (Exception e) {
            log.error("Job execution error jobId={} type={}", job.getId(), job.getType(), e);
            job.setStatus(JobStatus.FAILED);
        }

        if (job.getStatus() != JobStatus.RUNNING) {
            job.setRunningStartedAt(null);
        }

        jobRepository.save(job);
        log.info("Job finished jobId={} status={}", job.getId(), job.getStatus());
    }

    private void runEmail(JobEntity job) throws Exception {
        JsonNode root = readPayload(job);
        if (root.path("fail").asBoolean(false)) {
            job.setStatus(JobStatus.FAILED);
            return;
        }
        String to = root.path("to").asText("").trim();
        if (to.isEmpty()) {
            log.warn("EMAIL jobId={} missing non-blank 'to'", job.getId());
            job.setStatus(JobStatus.FAILED);
            return;
        }
        log.info("Simulated EMAIL sent jobId={} to={}", job.getId(), to);
        job.setStatus(JobStatus.COMPLETED);
    }

    private void runReport(JobEntity job) throws Exception {
        JsonNode root = readPayload(job);
        if (root.path("fail").asBoolean(false)) {
            job.setStatus(JobStatus.FAILED);
            return;
        }
        log.info("Simulated REPORT completed jobId={}", job.getId());
        job.setStatus(JobStatus.COMPLETED);
    }

    private void runExternal(JobEntity job) throws Exception {
        JsonNode root = readPayload(job);
        String webhookUrl = root.path("webhookUrl").asText("").trim();
        if (webhookUrl.isEmpty()) {
            log.warn("EXTERNAL jobId={} missing webhookUrl", job.getId());
            job.setStatus(JobStatus.FAILED);
            return;
        }

        String methodName = root.path("httpMethod").asText("POST").trim().toUpperCase();
        HttpMethod method;
        try {
            method = HttpMethod.valueOf(methodName);
        } catch (IllegalArgumentException e) {
            method = HttpMethod.POST;
        }

        ObjectNode outbound = objectMapper.createObjectNode();
        outbound.put("jobId", job.getId().toString());
        outbound.put("jobType", JobType.EXTERNAL.name());
        outbound.put(
                "message",
                "Complete this job by calling POST /jobs/callback on the API with jobId, status COMPLETED or FAILED, and optional detail."
        );
        if (root.has("body") && !root.get("body").isNull()) {
            outbound.set("body", root.get("body"));
        }

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        JsonNode headerNode = root.get("headers");
        if (headerNode != null && headerNode.isObject()) {
            Iterator<String> names = headerNode.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                headers.add(name, headerNode.get(name).asText());
            }
        }

        HttpEntity<String> entity = new HttpEntity<>(outbound.toString(), headers);
        try {
            ResponseEntity<String> response = restTemplate.exchange(webhookUrl, method, entity, String.class);
            if (response.getStatusCode().is2xxSuccessful()) {
                log.info(
                        "EXTERNAL webhook accepted jobId={} httpStatus={} (awaiting callback)",
                        job.getId(),
                        response.getStatusCode()
                );
            } else {
                log.warn("EXTERNAL webhook non-success jobId={} httpStatus={}", job.getId(), response.getStatusCode());
                job.setStatus(JobStatus.FAILED);
            }
        } catch (RestClientException e) {
            log.warn("EXTERNAL webhook failed jobId={}: {}", job.getId(), e.getMessage());
            job.setStatus(JobStatus.FAILED);
        }
    }

    private JsonNode readPayload(JobEntity job) throws Exception {
        String raw = job.getPayloadJson();
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("payload_json is blank");
        }
        return objectMapper.readTree(raw);
    }
}
