ALTER TABLE user_notification
    ADD COLUMN event_key VARCHAR(255) NULL;

CREATE UNIQUE INDEX uk_user_notification_recipient_event
    ON user_notification (recipient_profile_id, recipient_role, event_key);
