CREATE TABLE notification_broadcast (
    id CHAR(36) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    sender_profile_id CHAR(36) NOT NULL,
    sender_role VARCHAR(32) NOT NULL,
    audience VARCHAR(64) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    idempotency_key VARCHAR(128) NOT NULL,
    request_fingerprint VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL,
    recipient_count INT NOT NULL,
    notification_count INT NOT NULL,
    delivery_queued_count INT NOT NULL,
    completed_at DATETIME(6) NULL,
    CONSTRAINT pk_notification_broadcast PRIMARY KEY (id),
    CONSTRAINT uk_notification_broadcast_sender_key
        UNIQUE (sender_profile_id, sender_role, idempotency_key)
);

ALTER TABLE user_notification
    ADD COLUMN broadcast_id CHAR(36) NULL,
    ADD CONSTRAINT fk_user_notification_broadcast
        FOREIGN KEY (broadcast_id) REFERENCES notification_broadcast (id);

CREATE UNIQUE INDEX uk_user_notification_broadcast_recipient
    ON user_notification (broadcast_id, recipient_profile_id, recipient_role);
