package com.saga.be.entity;

import com.saga.be.entity.enums.Priority;
import com.saga.be.entity.enums.TaskStatus;
import com.saga.be.entity.enums.TaskType;
import com.saga.be.entity.converter.StringListJsonConverter;
import com.saga.be.entity.converter.TaskComponentListJsonConverter;
import com.saga.be.entity.value.TaskComponentSnapshot;
import jakarta.persistence.Convert;
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
import java.util.List;
import lombok.AccessLevel;

@Entity
@Table(name = "task")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Task extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sprint_id")
    private Sprint sprint;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assignee_id")
    private Student assignee;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_id")
    private Student reporter;

    @Column(name = "assignee_external_id")
    private String assigneeExternalId;

    @Column(name = "reporter_external_id")
    private String reporterExternalId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocks_task_id")
    private Task blocksTask;

    @Column(name = "external_key")
    private String externalKey;

    @Column(name = "external_id")
    private String externalId;

    @Column(name = "title")
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private TaskType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "status")
    private TaskStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "priority")
    private Priority priority;

    @Column(name = "story_point")
    private Integer storyPoint;

    @Column(name = "due_date")
    private LocalDateTime dueDate;

    @Column(name = "external_updated_at")
    private LocalDateTime externalUpdatedAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    @Column(name = "resolution")
    private String resolution;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;

    @Builder.Default
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Convert(converter = StringListJsonConverter.class)
    @Column(name = "labels_json", columnDefinition = "TEXT")
    private List<String> labels = List.of();

    @Builder.Default
    @Getter(AccessLevel.NONE)
    @Setter(AccessLevel.NONE)
    @Convert(converter = TaskComponentListJsonConverter.class)
    @Column(name = "components_json", columnDefinition = "TEXT")
    private List<TaskComponentSnapshot> components = List.of();

    public List<String> getLabels() {
        return labels == null ? List.of() : List.copyOf(labels);
    }

    public void setLabels(List<String> labels) {
        this.labels = labels == null ? List.of() : List.copyOf(labels);
    }

    public List<TaskComponentSnapshot> getComponents() {
        return components == null ? List.of() : List.copyOf(components);
    }

    public void setComponents(List<TaskComponentSnapshot> components) {
        this.components = components == null ? List.of() : List.copyOf(components);
    }
}
