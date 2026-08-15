package com.saga.be.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NotificationBroadcastMigrationContractTest {

    @Test
    void v26AddsIdempotentBroadcastMasterAndRecipientDedupWithoutChangingLegacyNotification() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V26__add_notification_broadcast_master.sql"
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").toLowerCase();

        assertThat(sql).contains(
                "create table notification_broadcast",
                "sender_profile_id char(36) not null",
                "idempotency_key varchar(128) not null",
                "unique (sender_profile_id, sender_role, idempotency_key)",
                "alter table user_notification",
                "add column broadcast_id char(36) null",
                "unique index uk_user_notification_broadcast_recipient"
        );
        assertThat(sql).doesNotContain("drop table", "alter table notification", "student_course_invitation");
    }

    @Test
    void v27AddsPerRecipientEventDedupKeyWithoutTouchingEnrollmentOrInvitations() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V27__add_notification_event_dedup_key.sql"
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").toLowerCase();

        assertThat(sql).contains(
                "alter table user_notification",
                "add column event_key varchar(255) null",
                "unique index uk_user_notification_recipient_event"
        );
        assertThat(sql).doesNotContain("drop table", "student_course", "student_course_invitation");
    }
}
