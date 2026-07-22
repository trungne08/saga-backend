package com.saga.be.config;

import com.saga.be.entity.SystemAuditLog;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(
        name = "app.mongodb.initialize-collections",
        havingValue = "true"
)
public class MongoCollectionInitializer implements ApplicationRunner {

    private final MongoTemplate mongoTemplate;

    @Override
    public void run(ApplicationArguments args) {
        String collectionName = mongoTemplate.getCollectionName(SystemAuditLog.class);
        if (!mongoTemplate.collectionExists(SystemAuditLog.class)) {
            mongoTemplate.createCollection(SystemAuditLog.class);
            log.info("Created MongoDB collection '{}'", collectionName);
        } else {
            log.info("MongoDB collection '{}' already exists", collectionName);
        }
    }
}
