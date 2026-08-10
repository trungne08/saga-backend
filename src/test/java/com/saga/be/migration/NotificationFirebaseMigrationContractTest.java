package com.saga.be.migration;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class NotificationFirebaseMigrationContractTest {

    @Test
    void v25AddsUserOwnedNotificationsInstallationsAndDurableDeliveryOnly() throws Exception {
        String sql = Files.readString(Path.of(
                "src", "main", "resources", "db", "migration",
                "V25__add_user_notification_and_firebase_delivery.sql"
        ), StandardCharsets.UTF_8).replace("\r\n", "\n").toLowerCase();

        assertThat(sql).contains(
                "create table user_notification",
                "create table firebase_installation",
                "create table notification_delivery",
                "recipient_profile_id char(36) not null",
                "recipient_role varchar(32) not null",
                "firebase_installation_id varchar(255) not null",
                "unique (firebase_installation_id)",
                "foreign key (notification_id) references user_notification (id)",
                "foreign key (installation_id) references firebase_installation (id)"
        );
        assertThat(sql).doesNotContain(
                "drop table",
                "alter table notification",
                "recipient_id",
                "firebase_service_account",
                "private_key"
        );
    }
}
