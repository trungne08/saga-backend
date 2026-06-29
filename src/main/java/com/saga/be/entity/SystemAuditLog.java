package com.saga.be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;

@Entity
@Table(name = "system_audit_log")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemAuditLog extends BaseEntity {

    @Column(name = "actor_id")
    private UUID actorId;

    @Column(name = "actor_role")
    private String actorRole;

    @Column(name = "action")
    private String action;

    @Column(name = "table_name")
    private String tableName;

    @Column(name = "record_id")
    private String recordId;

    @Column(name = "old_values", columnDefinition = "TEXT")
    private String oldValues;

    @Column(name = "new_values", columnDefinition = "TEXT")
    private String newValues;

    @Column(name = "ip_address")
    private String ipAddress;

    @Column(name = "endpoint")
    private String endpoint;
}
