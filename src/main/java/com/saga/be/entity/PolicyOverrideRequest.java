package com.saga.be.entity;

import com.saga.be.entity.enums.PolicyOverrideStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "policy_override_request")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PolicyOverrideRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private Class clazz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "lecturer_id")
    private Lecturer lecturer;

    @Column(name = "target_config_id", nullable = true)
    private UUID targetConfigId;

    @Column(name = "type")
    private String type;

    @Column(name = "proposed_threshold", nullable = true)
    private Float proposedThreshold;

    @Column(name = "proposed_weight", nullable = true)
    private Float proposedWeight;

    @Column(name = "reason", columnDefinition = "TEXT")
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private PolicyOverrideStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by", nullable = true)
    private Admin resolvedBy;

    @Column(name = "resolved_at", nullable = true)
    private LocalDateTime resolvedAt;
}
