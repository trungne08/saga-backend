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
    private String action;
    private String targetEntity;

    private Object oldValues; // Hứng cục JSON cũ
    private Object newValues; // Hứng cục JSON mới

    private LocalDateTime timestamp = LocalDateTime.now();
    private String ipAddress;
}