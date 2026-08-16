ALTER TABLE ai_agent_delegation_context
    ADD COLUMN course_id CHAR(36) NULL;

CREATE TABLE ai_agent_conversation_scope (
    id CHAR(36) NOT NULL,
    conversation_id CHAR(36) NOT NULL,
    course_id CHAR(36) NOT NULL,
    owner_profile_id CHAR(36) NOT NULL,
    owner_application_role VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    PRIMARY KEY (id),
    CONSTRAINT uk_ai_agent_conversation_scope_conversation UNIQUE (conversation_id),
    INDEX ix_ai_agent_conversation_scope_owner (owner_profile_id, owner_application_role)
);
