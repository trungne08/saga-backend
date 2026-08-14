package com.saga.be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.UUID;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import java.sql.Types;

@Entity
@Table(name = "project_group_weight_config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectGroupWeightConfig extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", unique = true, nullable = false)
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id", nullable = false)
    private Team team;

    @Column(name = "code_weight", precision = 6, scale = 5, nullable = false)
    private BigDecimal codeWeight;

    @Column(name = "document_weight", precision = 6, scale = 5, nullable = false)
    private BigDecimal documentWeight;

    @Column(name = "design_weight", precision = 6, scale = 5, nullable = false)
    private BigDecimal designWeight;

    @Column(name = "note", length = 1000)
    private String note;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "updated_by_profile_id")
    private UUID updatedByProfileId;
}
