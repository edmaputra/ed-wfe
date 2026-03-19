CREATE TABLE IF NOT EXISTS workflow_definitions (
    id BIGSERIAL PRIMARY KEY,
    workflow_key VARCHAR(100) NOT NULL,
    version INTEGER NOT NULL,
    name VARCHAR(200) NOT NULL,
    definition_json JSONB NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uq_workflow_definitions_key_version UNIQUE (workflow_key, version)
);

CREATE TABLE IF NOT EXISTS workflow_instances (
    id BIGSERIAL PRIMARY KEY,
    definition_id BIGINT NOT NULL REFERENCES workflow_definitions(id),
    status VARCHAR(40) NOT NULL,
    current_step VARCHAR(120),
    context_json JSONB,
    started_at TIMESTAMP,
    ended_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE IF NOT EXISTS workflow_execution_logs (
    id BIGSERIAL PRIMARY KEY,
    instance_id BIGINT NOT NULL REFERENCES workflow_instances(id),
    step_name VARCHAR(120),
    event_type VARCHAR(80) NOT NULL,
    message TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP
);
