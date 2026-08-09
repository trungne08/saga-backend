package com.saga.be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/** Typed singleton setting. The only permitted row has singletonId = 1. */
@Entity
@Table(name = "active_semester_setting")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class ActiveSemesterSetting {

    public static final byte SINGLETON_ID = 1;

    @Id
    @Column(name = "singleton_id", nullable = false, updatable = false)
    private byte singletonId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "semester_id")
    private Semester semester;
}
