package com.saga.be.entity;

import com.saga.be.security.ApplicationRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.sql.Types;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "ai_agent_conversation_scope")
@Getter
@Setter
@NoArgsConstructor
public class AiAgentConversationScope extends BaseEntity {

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "conversation_id", nullable = false, unique = true, columnDefinition = "char(36)")
    private UUID conversationId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "course_id", nullable = false, columnDefinition = "char(36)")
    private UUID courseId;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "owner_profile_id", nullable = false, columnDefinition = "char(36)")
    private UUID ownerProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_application_role", nullable = false, length = 32)
    private ApplicationRole ownerApplicationRole;
}
