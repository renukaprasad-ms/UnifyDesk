-- V123__crm_automation_rules.sql
-- Event-Driven CRM Automation Rules & Triggers

CREATE TABLE IF NOT EXISTS crm_automation_rules (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    name VARCHAR(120) NOT NULL,
    description VARCHAR(500),
    is_enabled BOOLEAN NOT NULL DEFAULT TRUE,
    trigger_type VARCHAR(64) NOT NULL,
    trigger_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    action_type VARCHAR(64) NOT NULL,
    action_config JSONB NOT NULL DEFAULT '{}'::jsonb,
    execution_count BIGINT NOT NULL DEFAULT 0,
    last_executed_at TIMESTAMPTZ,
    created_by_user_id BIGINT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_crm_auto_rules_company_trigger 
    ON crm_automation_rules (company_id, is_enabled, trigger_type);

CREATE TABLE IF NOT EXISTS crm_automation_logs (
    id BIGINT PRIMARY KEY,
    company_id BIGINT NOT NULL,
    rule_id BIGINT NOT NULL REFERENCES crm_automation_rules(id) ON DELETE CASCADE,
    rule_name VARCHAR(120) NOT NULL,
    trigger_type VARCHAR(64) NOT NULL,
    entity_type VARCHAR(32) NOT NULL,
    entity_id VARCHAR(64),
    action_type VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    details TEXT,
    executed_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE INDEX IF NOT EXISTS idx_crm_auto_logs_company_time 
    ON crm_automation_logs (company_id, executed_at DESC);

CREATE INDEX IF NOT EXISTS idx_crm_auto_logs_rule_time 
    ON crm_automation_logs (rule_id, executed_at DESC);

CREATE TABLE IF NOT EXISTS crm_round_robin_state (
    company_id BIGINT PRIMARY KEY,
    last_assigned_user_id BIGINT,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
