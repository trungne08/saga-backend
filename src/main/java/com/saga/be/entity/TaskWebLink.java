package com.saga.be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "task_web_link")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskWebLink extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "url", nullable = false, length = 2048)
    private String url;

    @Column(name = "title", length = 255)
    private String title;

    @Column(name = "remote_link_id", length = 64)
    private String remoteLinkId;
}
