package com.saga.be.config;

import com.saga.be.service.StudentInvitationDeliveryAdapter;
import com.saga.be.service.GmailSmtpStudentInvitationDeliveryAdapter;
import com.saga.be.service.UnavailableStudentInvitationDeliveryAdapter;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.util.StringUtils;

@Configuration
public class StudentInvitationDeliveryConfiguration {

    @Bean
    @ConditionalOnMissingBean(StudentInvitationDeliveryAdapter.class)
    StudentInvitationDeliveryAdapter studentInvitationDeliveryAdapter(
            ObjectProvider<JavaMailSender> mailSenderProvider,
            Environment environment
    ) {
        String host = environment.getProperty("spring.mail.host");
        Integer port = environment.getProperty("spring.mail.port", Integer.class);
        String username = environment.getProperty("spring.mail.username");
        String password = environment.getProperty("spring.mail.password");
        boolean authenticationEnabled = environment.getProperty(
                "spring.mail.properties.mail.smtp.auth",
                Boolean.class,
                false
        );
        boolean startTlsEnabled = environment.getProperty(
                "spring.mail.properties.mail.smtp.starttls.enable",
                Boolean.class,
                false
        );
        JavaMailSender mailSender = mailSenderProvider.getIfAvailable();

        if (!StringUtils.hasText(host)
                || port == null
                || port <= 0
                || !StringUtils.hasText(username)
                || !StringUtils.hasText(password)
                || !authenticationEnabled
                || !startTlsEnabled
                || mailSender == null) {
            return new UnavailableStudentInvitationDeliveryAdapter();
        }

        return new GmailSmtpStudentInvitationDeliveryAdapter(mailSender, username.trim());
    }
}
