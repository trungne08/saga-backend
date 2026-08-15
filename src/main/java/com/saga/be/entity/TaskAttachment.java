package com.saga.be.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(
        name = "task_attachment",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_task_attachment_external",
                columnNames = {"task_id", "external_id"}
        )
)
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TaskAttachment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "task_id", nullable = false)
    private Task task;

    @Column(name = "external_id", nullable = false, length = 64)
    private String externalId;

    @Column(name = "filename", length = 512)
    private String filename;

    @Column(name = "mime_type", length = 255)
    private String mimeType;

    @Column(name = "size_bytes")
    private Long sizeBytes;

    @Column(name = "author_external_id", length = 128)
    private String authorExternalId;
}
