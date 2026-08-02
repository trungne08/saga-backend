package com.saga.be.config;

import com.saga.be.service.StudentInvitationDeliveryAdapter;
import com.saga.be.service.UnavailableStudentInvitationDeliveryAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class StudentInvitationDeliveryConfiguration {

    @Bean
    @ConditionalOnMissingBean(StudentInvitationDeliveryAdapter.class)
    StudentInvitationDeliveryAdapter unavailableStudentInvitationDeliveryAdapter() {
        return new UnavailableStudentInvitationDeliveryAdapter();
    }
}
