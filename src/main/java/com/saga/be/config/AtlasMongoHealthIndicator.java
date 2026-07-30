package com.saga.be.config;

import com.mongodb.client.MongoDatabase;
import jakarta.annotation.PreDestroy;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.Supplier;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.bson.Document;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

/**
 * Atlas-compatible MongoDB health check. It uses the application's configured
 * database and deliberately never targets the MongoDB {@code local} database.
 */
@Component
@ConditionalOnProperty(name = "app.mongodb.health.enabled", havingValue = "true", matchIfMissing = true)
public class AtlasMongoHealthIndicator implements HealthIndicator {

    private static final Document PING_COMMAND = new Document("ping", 1);

    private final Supplier<MongoDatabaseFactory> databaseFactorySupplier;
    private final Duration timeout;
    private final ExecutorService executor;

    @Autowired
    public AtlasMongoHealthIndicator(
            ObjectProvider<MongoDatabaseFactory> databaseFactory,
            AtlasMongoHealthProperties properties
    ) {
        this(databaseFactory::getObject, properties.timeout(), newHealthExecutor());
    }

    AtlasMongoHealthIndicator(
            MongoDatabaseFactory databaseFactory,
            Duration timeout,
            ExecutorService executor
    ) {
        this(() -> databaseFactory, timeout, executor);
    }

    private AtlasMongoHealthIndicator(
            Supplier<MongoDatabaseFactory> databaseFactorySupplier,
            Duration timeout,
            ExecutorService executor
    ) {
        this.databaseFactorySupplier = databaseFactorySupplier;
        this.timeout = timeout;
        this.executor = executor;
    }

    @Override
    public Health health() {
        Future<Document> ping = executor.submit(this::pingApplicationDatabase);
        try {
            ping.get(timeout.toMillis(), TimeUnit.MILLISECONDS);
            return Health.up().build();
        } catch (TimeoutException exception) {
            ping.cancel(true);
            return Health.down().withDetail("errorType", "timeout").build();
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            return Health.down().withDetail("errorType", "interrupted").build();
        } catch (ExecutionException exception) {
            return Health.down().withDetail("errorType", "mongo_error").build();
        } catch (RuntimeException exception) {
            return Health.down().withDetail("errorType", "mongo_error").build();
        }
    }

    private Document pingApplicationDatabase() {
        MongoDatabase applicationDatabase = databaseFactorySupplier.get().getMongoDatabase();
        return applicationDatabase.runCommand(PING_COMMAND);
    }

    @PreDestroy
    void shutdown() {
        executor.shutdownNow();
    }

    private static ExecutorService newHealthExecutor() {
        ThreadFactory threadFactory = runnable -> {
            Thread thread = new Thread(runnable, "atlas-mongo-health");
            thread.setDaemon(true);
            return thread;
        };
        return Executors.newSingleThreadExecutor(threadFactory);
    }
}
