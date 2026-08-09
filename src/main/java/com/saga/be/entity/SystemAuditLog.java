package com.saga.be.entity; // Nhớ check lại tên package của ní nha

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;
import lombok.Data;
import java.time.LocalDateTime;

@Data // Dùng Lombok cho gọn, khỏi tự gen Getter/Setter
@Document(collection = "system_audit_log")
public class SystemAuditLog {

    @Id
    private String id; // Mongo dùng String làm ID

    private String actorId;
    /**
     * Stable local profile identity for newly written user-originated events.
     * Stored as canonical UUID text so Mongo does not require UUID binary
     * representation configuration; absent for system-originated events.
     */
    private String actorLocalProfileId;
    /** Application role when the producer has an exact authenticated role. */
    private String actorRole;
    private String action;
    private String targetEntity;

    private Object oldValues; // Hứng cục JSON cũ
    private Object newValues; // Hứng cục JSON mới

    private LocalDateTime timestamp = LocalDateTime.now();
    private String ipAddress;
}
