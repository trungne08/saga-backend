package com.saga.be.repository;

import com.saga.be.entity.SystemAuditLog;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface SystemAuditLogRepository extends MongoRepository<SystemAuditLog, String> {
}
