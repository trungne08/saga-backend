package com.saga.be.config;

import com.saga.be.service.GmailApiStudentInvitationDeliveryAdapter;
import com.saga.be.service.StudentInvitationDeliveryAdapter;
import com.saga.be.service.UnavailableStudentInvitationDeliveryAdapter;
import java.net.http.HttpClient;
import java.time.Duration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import tools.jackson.databind.ObjectMapper;

@Configuration
public class StudentInvitationDeliveryConfiguration {

    @Bean
    @ConditionalOnMissingBean(StudentInvitationDeliveryAdapter.class)
    StudentInvitationDeliveryAdapter studentInvitationDeliveryAdapter(
            GmailApiStudentInvitationProperties gmailProperties,
            IntegrationProperties integrationProperties,
            ObjectMapper objectMapper
    ) {
        if (!StringUtils.hasText(gmailProperties.clientId())
                || !StringUtils.hasText(gmailProperties.clientSecret())
                || !StringUtils.hasText(gmailProperties.refreshToken())
                || !StringUtils.hasText(gmailProperties.senderEmail())
                || !StringUtils.hasText(gmailProperties.senderName())) {
            return new UnavailableStudentInvitationDeliveryAdapter();
        }

        RestClient restClient = RestClient.builder()
                .requestFactory(requestFactory(integrationProperties))
                .build();
        return new GmailApiStudentInvitationDeliveryAdapter(
                restClient,
                objectMapper,
                gmailProperties.clientId().trim(),
                gmailProperties.clientSecret().trim(),
                gmailProperties.refreshToken().trim(),
                gmailProperties.senderEmail().trim(),
                gmailProperties.senderName().trim()
        );
    }

    private JdkClientHttpRequestFactory requestFactory(
            IntegrationProperties integrationProperties
    ) {
        Duration connect = integrationProperties.httpConnectTimeout() == null
                ? Duration.ofSeconds(3)
                : integrationProperties.httpConnectTimeout();
        Duration read = integrationProperties.httpReadTimeout() == null
                ? Duration.ofSeconds(10)
                : integrationProperties.httpReadTimeout();
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(connect)
                .build();
        JdkClientHttpRequestFactory factory =
                new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(read);
        return factory;
    }
}
