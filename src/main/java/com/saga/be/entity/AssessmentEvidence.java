package com.saga.be.entity;

import com.saga.be.entity.enums.ArtifactType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.util.UUID;
import java.sql.Types;

@Entity
@Table(name = "assessment_evidence")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssessmentEvidence extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessment_id")
    private Assessment assessment;

    @Enumerated(EnumType.STRING)
    @Column(name = "artifact_type")
    private ArtifactType artifactType;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "artifact_id", columnDefinition = "char(36)")
    private UUID artifactId;
}
