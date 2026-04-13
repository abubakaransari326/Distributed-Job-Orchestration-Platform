CREATE TABLE jobs (
    id UUID PRIMARY KEY,
    type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL,
    payload_json TEXT,
    retries INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL
);
