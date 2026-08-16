package com.saga.be.config;

import java.time.Clock;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class CourseStatusTimeConfiguration {

    @Bean
    @ConditionalOnMissingBean(Clock.class)
    Clock courseStatusClock() {
        return Clock.systemUTC();
    }
}
