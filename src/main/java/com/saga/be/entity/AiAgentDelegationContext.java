package com.saga.be.entity;

import com.saga.be.security.ApplicationRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.UUID;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;

@Entity
@Table(name = "ai_agent_delegation_context")
@Getter
@Setter
@NoArgsConstructor
public class AiAgentDelegationContext extends BaseEntity {

    @Column(name = "token_hash", nullable = false, length = 64, unique = true)
    private String tokenHash;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "conversation_id", nullable = false, columnDefinition = "char(36)")
    private UUID conversationId;

    @Column(name = "actor_cognito_sub", nullable = false)
    private String actorCognitoSub;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "actor_profile_id", nullable = false, columnDefinition = "char(36)")
    private UUID actorProfileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_application_role", nullable = false, length = 32)
    private ApplicationRole actorApplicationRole;

    @Column(name = "capabilities", nullable = false, length = 128)
    private String capabilities;

    @Column(name = "expires_at", nullable = false)
    private LocalDateTime expiresAt;

    @JdbcTypeCode(Types.CHAR)
    @Column(name = "course_id", columnDefinition = "char(36)")
    private UUID courseId;
}

