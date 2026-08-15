package com.saga.be.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.saga.be.entity.enums.ContributionConfigMode;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import java.time.LocalDateTime;

@Entity
@Table(name = "course")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Course extends BaseEntity {
    private static final double DEFAULT_SLICE_WEIGHT = 25.0;
    /** design_contribution_weight is retained-but-inactive; new rows get a neutral 0. */
    private static final double LEGACY_INACTIVE_WEIGHT = 0.0;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "subject_id")
    private Subject subject;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "class_id")
    private Class clazz;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id")
    private Semester semester;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "instructor_id")
    private Lecturer instructor;

    @Column(name = "course_code")
    private String courseCode;

    @Column(name = "name")
    private String name;

    @Column(name = "code_contribution_weight", nullable = false)
    private Double codeContributionWeight;

    @Column(name = "test_contribution_weight", nullable = false)
    private Double testContributionWeight;

    @Column(name = "document_contribution_weight", nullable = false)
    private Double documentContributionWeight;

    @Column(name = "research_contribution_weight", nullable = false)
    private Double researchContributionWeight;

    /** Legacy column from the retired DESIGN criterion; retained for historical rows, inactive. */
    @Column(name = "design_contribution_weight", nullable = false)
    private Double designContributionWeight;

    @Enumerated(EnumType.STRING)
    @Column(name = "contribution_config_mode", nullable = false)
    private ContributionConfigMode contributionConfigMode;

    @JsonIgnore
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    @PrePersist
    void applyDefaultContributionWeights() {
        if (codeContributionWeight == null) {
            codeContributionWeight = DEFAULT_SLICE_WEIGHT;
        }
        if (testContributionWeight == null) {
            testContributionWeight = DEFAULT_SLICE_WEIGHT;
        }
        if (documentContributionWeight == null) {
            documentContributionWeight = DEFAULT_SLICE_WEIGHT;
        }
        if (researchContributionWeight == null) {
            researchContributionWeight = DEFAULT_SLICE_WEIGHT;
        }
        if (designContributionWeight == null) {
            designContributionWeight = LEGACY_INACTIVE_WEIGHT;
        }
        if (contributionConfigMode == null) {
            contributionConfigMode = ContributionConfigMode.COURSE;
        }
    }
}
