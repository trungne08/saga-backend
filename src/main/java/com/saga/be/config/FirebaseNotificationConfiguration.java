package com.saga.be.config;

import com.saga.be.service.FirebaseAdminNotificationDeliveryAdapter;
import com.saga.be.service.FirebaseNotificationDeliveryAdapter;
import com.saga.be.service.UnavailableFirebaseNotificationDeliveryAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class FirebaseNotificationConfiguration {

    @Bean
    @ConditionalOnMissingBean(FirebaseNotificationDeliveryAdapter.class)
    FirebaseNotificationDeliveryAdapter firebaseNotificationDeliveryAdapter(
            FirebaseAdminInitializer initializer,
            Environment environment
    ) {
        return initializer.initialize(environment)
                .<FirebaseNotificationDeliveryAdapter>map(
                        FirebaseAdminNotificationDeliveryAdapter::new
                )
                .orElseGet(UnavailableFirebaseNotificationDeliveryAdapter::new);
    }
}
