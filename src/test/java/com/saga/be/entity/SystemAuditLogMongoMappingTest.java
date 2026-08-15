package com.saga.be.entity;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;

import java.time.Instant;
import java.util.Date;
import org.bson.Document;
import org.junit.jupiter.api.Test;
import org.springframework.data.mongodb.core.convert.MappingMongoConverter;
import org.springframework.data.mongodb.core.convert.MongoCustomConversions;
import org.springframework.data.mongodb.core.convert.NoOpDbRefResolver;
import org.springframework.data.mongodb.core.mapping.MongoMappingContext;

class SystemAuditLogMongoMappingTest {

    @Test
    void persistsInstantAsBsonDateAndReadsHistoricalBsonDateWithoutReinterpretation() throws Exception {
        Instant eventTime = Instant.parse("2026-08-09T16:30:00Z");
        MappingMongoConverter converter = converter();

        SystemAuditLog newLog = new SystemAuditLog();
        newLog.setId("new");
        newLog.setTimestamp(eventTime);
        Document persisted = new Document();
        converter.write(newLog, persisted);

        assertInstanceOf(Date.class, persisted.get("timestamp"));
        assertEquals(eventTime, ((Date) persisted.get("timestamp")).toInstant());

        Document historical = new Document("_id", "historical")
                .append("timestamp", Date.from(eventTime));
        SystemAuditLog restored = converter.read(SystemAuditLog.class, historical);
        assertEquals(eventTime, restored.getTimestamp());
    }

    private MappingMongoConverter converter() throws Exception {
        MongoCustomConversions conversions = MongoCustomConversions.create(adapter -> { });
        MongoMappingContext context = new MongoMappingContext();
        context.setSimpleTypeHolder(conversions.getSimpleTypeHolder());
        context.afterPropertiesSet();
        MappingMongoConverter converter = new MappingMongoConverter(NoOpDbRefResolver.INSTANCE, context);
        converter.setCustomConversions(conversions);
        converter.afterPropertiesSet();
        return converter;
    }
}
