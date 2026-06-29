package com.saga.be.entity;

import com.saga.be.entity.enums.BoardType;
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

@Entity
@Table(name = "jira_board")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class JiraBoard extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id")
    private Project project;

    @Column(name = "name")
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "type")
    private BoardType type;

    @Column(name = "jira_board_id")
    private String jiraBoardId;

    @Column(name = "last_synced_at")
    private LocalDateTime lastSyncedAt;
}
