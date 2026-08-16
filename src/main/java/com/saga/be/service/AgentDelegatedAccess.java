package com.saga.be.service;

import com.saga.be.security.SagaPrincipal;
import java.util.UUID;

public record AgentDelegatedAccess(SagaPrincipal actor, UUID courseId) {
}
