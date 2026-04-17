-- When a job enters RUNNING, the worker sets running_started_at (used for EXTERNAL watchdog timeouts).
ALTER TABLE jobs ADD COLUMN running_started_at TIMESTAMPTZ;

UPDATE jobs
SET running_started_at = updated_at
WHERE status = 'RUNNING'
  AND running_started_at IS NULL;

-- Listing / filtering (GET /jobs with status + type).
CREATE INDEX idx_jobs_status_type ON jobs (status, type);

-- EXTERNAL jobs stuck in RUNNING (timeout scan).
CREATE INDEX idx_jobs_external_running_started
    ON jobs (running_started_at)
    WHERE type = 'EXTERNAL' AND status = 'RUNNING';
