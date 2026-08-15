package com.saga.be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "project_type")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ProjectType extends BaseEntity {

    /** Fixed canonical SAGA catalog codes seeded by {@code V34__replace_project_type_with_canonical_catalog.sql}. */
    public static final String CODE_DESIGN_ARCHITECTURE = "DESIGN_ARCHITECTURE";
    public static final String CODE_RESEARCH = "RESEARCH";
    public static final String CODE_TESTER = "TESTER";
    public static final String CODE_DOCUMENT = "DOCUMENT";

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "description", length = 1000)
    private String description;

    @Column(name = "criteria_config", columnDefinition = "TEXT")
    private String criteriaConfig;
}
