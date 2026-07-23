package com.saga.be;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.AuthorizationGrantType;

@TestConfiguration(proxyBeanMethods = false)
public class OAuth2TestConfiguration {

    @Bean
    ClientRegistrationRepository clientRegistrationRepository() {
        ClientRegistration registration = ClientRegistration
                .withRegistrationId("cognito")
                .clientId("test-client")
                .clientSecret("test-secret")
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
                .redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
                .scope("openid", "email", "profile")
                .authorizationUri("https://cognito.test/oauth2/authorize")
                .tokenUri("https://cognito.test/oauth2/token")
                .jwkSetUri("https://cognito.test/.well-known/jwks.json")
                .userInfoUri("https://cognito.test/oauth2/userInfo")
                .userNameAttributeName("sub")
                .clientName("Amazon Cognito")
                .build();
        return new InMemoryClientRegistrationRepository(registration);
    }
}
