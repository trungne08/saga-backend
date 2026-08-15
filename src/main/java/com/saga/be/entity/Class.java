package com.saga.be.entity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "class")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Class extends BaseEntity {

    @Column(name = "class_code")
    private String classCode;

    @Column(name = "name")
    private String name;

    @JsonIgnore
    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;
}
