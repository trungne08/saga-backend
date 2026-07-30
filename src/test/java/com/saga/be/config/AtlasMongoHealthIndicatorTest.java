package com.saga.be.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.mongodb.MongoException;
import com.mongodb.client.MongoDatabase;
import java.time.Duration;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import org.bson.Document;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.health.contributor.Status;
import org.springframework.data.mongodb.MongoDatabaseFactory;

class AtlasMongoHealthIndicatorTest {

    private final ExecutorService executor = Executors.newSingleThreadExecutor();

    @AfterEach
    void tearDown() {
        executor.shutdownNow();
    }

    @Test
    void pingsTheConfiguredApplicationDatabaseAndReturnsUp() {
        MongoDatabaseFactory databaseFactory = mock(MongoDatabaseFactory.class);
        MongoDatabase applicationDatabase = mock(MongoDatabase.class);
        when(databaseFactory.getMongoDatabase()).thenReturn(applicationDatabase);
        when(applicationDatabase.runCommand(any(Document.class))).thenReturn(new Document("ok", 1));
        AtlasMongoHealthIndicator indicator = new AtlasMongoHealthIndicator(
                databaseFactory, Duration.ofSeconds(1), executor);

        var health = indicator.health();

        assertEquals(Status.UP, health.getStatus());
        ArgumentCaptor<Document> command = ArgumentCaptor.forClass(Document.class);
        verify(applicationDatabase).runCommand(command.capture());
        assertEquals(new Document("ping", 1), command.getValue());
        verify(databaseFactory, never()).getMongoDatabase("local");
    }

    @Test
    void returnsSafeDownDetailsWhenMongoPingFails() {
        MongoDatabaseFactory databaseFactory = mock(MongoDatabaseFactory.class);
        MongoDatabase applicationDatabase = mock(MongoDatabase.class);
        when(databaseFactory.getMongoDatabase()).thenReturn(applicationDatabase);
        when(applicationDatabase.runCommand(any(Document.class))).thenThrow(
                new MongoException("mongodb://username:password@atlas.example/saga_db"));
        AtlasMongoHealthIndicator indicator = new AtlasMongoHealthIndicator(
                databaseFactory, Duration.ofSeconds(1), executor);

        var health = indicator.health();

        assertEquals(Status.DOWN, health.getStatus());
        assertThat(health.getDetails()).containsEntry("errorType", "mongo_error");
        assertThat(health.getDetails().toString())
                .doesNotContain("mongodb://", "username", "password", "atlas.example");
    }
}
