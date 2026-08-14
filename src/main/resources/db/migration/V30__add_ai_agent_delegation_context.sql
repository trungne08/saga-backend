CREATE TABLE ai_agent_delegation_context (
    id CHAR(36) NOT NULL,
    token_hash CHAR(64) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    actor_cognito_sub VARCHAR(255) NOT NULL,
    actor_profile_id CHAR(36) NOT NULL,
    actor_application_role VARCHAR(32) NOT NULL,
    capabilities VARCHAR(128) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_agent_delegation_token_hash UNIQUE (token_hash),
    INDEX ix_ai_agent_delegation_expiry (expires_at),
    INDEX ix_ai_agent_delegation_conversation_actor
        (conversation_id, actor_profile_id)
);

