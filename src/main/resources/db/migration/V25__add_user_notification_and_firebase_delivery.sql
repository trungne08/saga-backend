CREATE TABLE user_notification (
    id CHAR(36) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    recipient_profile_id CHAR(36) NOT NULL,
    recipient_role VARCHAR(32) NOT NULL,
    notification_type VARCHAR(64) NOT NULL,
    title VARCHAR(160) NOT NULL,
    message VARCHAR(1000) NOT NULL,
    action_url VARCHAR(500) NULL,
    read_at DATETIME(6) NULL,
    CONSTRAINT pk_user_notification PRIMARY KEY (id),
    INDEX ix_user_notification_owner_created (
        recipient_profile_id,
        recipient_role,
        created_at,
        id
    ),
    INDEX ix_user_notification_owner_unread (
        recipient_profile_id,
        recipient_role,
        read_at
    )
);

CREATE TABLE firebase_installation (
    id CHAR(36) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    owner_profile_id CHAR(36) NOT NULL,
    owner_role VARCHAR(32) NOT NULL,
    firebase_installation_id VARCHAR(255) NOT NULL,
    active BOOLEAN NOT NULL,
    last_registered_at DATETIME(6) NOT NULL,
    revoked_at DATETIME(6) NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_firebase_installation PRIMARY KEY (id),
    CONSTRAINT uk_firebase_installation_fid UNIQUE (firebase_installation_id),
    INDEX ix_firebase_installation_owner_active (
        owner_profile_id,
        owner_role,
        active
    )
);

CREATE TABLE notification_delivery (
    id CHAR(36) NOT NULL,
    created_at DATETIME(6) NULL,
    updated_at DATETIME(6) NULL,
    notification_id CHAR(36) NOT NULL,
    installation_id CHAR(36) NOT NULL,
    delivery_status VARCHAR(32) NOT NULL,
    attempt_count INT NOT NULL,
    last_attempt_at DATETIME(6) NULL,
    processing_started_at DATETIME(6) NULL,
    sent_at DATETIME(6) NULL,
    failure_code VARCHAR(64) NULL,
    version BIGINT NOT NULL,
    CONSTRAINT pk_notification_delivery PRIMARY KEY (id),
    CONSTRAINT uk_notification_delivery_installation
        UNIQUE (notification_id, installation_id),
    CONSTRAINT fk_notification_delivery_notification
        FOREIGN KEY (notification_id) REFERENCES user_notification (id),
    CONSTRAINT fk_notification_delivery_installation
        FOREIGN KEY (installation_id) REFERENCES firebase_installation (id),
    INDEX ix_notification_delivery_status_created (delivery_status, created_at),
    INDEX ix_notification_delivery_status_processing (
        delivery_status,
        processing_started_at
    )
);
