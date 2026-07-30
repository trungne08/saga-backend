package com.saga.be.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

import com.mongodb.client.MongoClient;
import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.AutoConfigurations;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.health.autoconfigure.actuate.endpoint.HealthEndpointAutoConfiguration;
import org.springframework.boot.health.autoconfigure.registry.HealthContributorRegistryAutoConfiguration;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.mongodb.autoconfigure.MongoAutoConfiguration;
import org.springframework.boot.mongodb.autoconfigure.health.MongoHealthContributorAutoConfiguration;
import org.springframework.boot.mongodb.health.MongoHealthIndicator;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.FilterType;

class AtlasMongoHealthConfigurationTest {

    private final ApplicationContextRunner contextRunner = new ApplicationContextRunner()
            .withConfiguration(AutoConfigurations.of(
                    MongoAutoConfiguration.class,
                    MongoHealthContributorAutoConfiguration.class,
                    HealthContributorRegistryAutoConfiguration.class,
                    HealthEndpointAutoConfiguration.class
            ))
            .withUserConfiguration(HealthTestConfiguration.class)
            // This is the same reserved Boot bean name. Mocking it keeps the context test offline.
            .withBean("mongo", MongoClient.class, () -> mock(MongoClient.class))
            .withPropertyValues(
                    "management.health.mongodb.enabled=false",
                    "app.mongodb.health.enabled=true",
                    "app.mongodb.health.timeout=1s"
            );

    @Test
    void startsWithMongoAutoConfigurationAndRegistersOnlyTheCustomAtlasContributor() {
        contextRunner.run(context -> {
            assertThat(context).hasBean("mongo");
            assertThat(context.getBean("mongo")).isInstanceOf(MongoClient.class);
            assertThat(context).hasBean("atlasMongoHealthIndicator");
            assertThat(context.getBean("atlasMongoHealthIndicator"))
                    .isInstanceOf(AtlasMongoHealthIndicator.class);
            assertThat(context.getBeansOfType(MongoHealthIndicator.class)).isEmpty();
            assertThat(context).doesNotHaveBean("mongoHealthContributor");
            HealthContributorRegistry registry = context.getBean(HealthContributorRegistry.class);
            assertThat(registry.getContributor("atlasMongo")).isInstanceOf(AtlasMongoHealthIndicator.class);
            assertThat(registry.getContributor("mongo")).isNull();
        });
    }

    @Configuration(proxyBeanMethods = false)
    @EnableConfigurationProperties(AtlasMongoHealthProperties.class)
    @ComponentScan(
            basePackageClasses = AtlasMongoHealthIndicator.class,
            useDefaultFilters = false,
            includeFilters = @ComponentScan.Filter(
                    type = FilterType.ASSIGNABLE_TYPE,
                    classes = AtlasMongoHealthIndicator.class
            )
    )
    static class HealthTestConfiguration {
    }
}
