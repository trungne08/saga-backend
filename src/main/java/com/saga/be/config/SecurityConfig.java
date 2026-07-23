package com.saga.be.config;

import com.saga.be.security.CognitoAuthenticationFailureHandler;
import com.saga.be.security.CognitoAuthenticationSuccessHandler;
import com.saga.be.security.CognitoLogoutSuccessHandler;
import com.saga.be.security.CognitoOidcAuthoritiesMapper;
import com.saga.be.security.JsonAccessDeniedHandler;
import com.saga.be.security.JsonAuthenticationEntryPoint;
import com.saga.be.security.NoStoreOAuth2AuthorizedClientRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.oauth2.client.web.OAuth2LoginAuthenticationFilter;
import org.springframework.web.cors.CorsConfigurationSource;

@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    public SecurityContextRepository securityContextRepository() {
        return new HttpSessionSecurityContextRepository();
    }

    @Bean
    public CsrfTokenRepository csrfTokenRepository(
            @Value("${server.servlet.session.cookie.secure:false}") boolean secure,
            @Value("${server.servlet.session.cookie.same-site:lax}") String sameSite
    ) {
        CookieCsrfTokenRepository repository =
                CookieCsrfTokenRepository.withHttpOnlyFalse();
        repository.setCookieCustomizer(cookie -> cookie
                .path("/")
                .httpOnly(false)
                .secure(secure)
                .sameSite(sameSite)
        );
        return repository;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(
            HttpSecurity http,
            CorsConfigurationSource corsConfigurationSource,
            CsrfTokenRepository csrfTokenRepository,
            SecurityContextRepository securityContextRepository,
            CognitoOidcAuthoritiesMapper authoritiesMapper,
            NoStoreOAuth2AuthorizedClientRepository authorizedClientRepository,
            CognitoAuthenticationSuccessHandler successHandler,
            CognitoAuthenticationFailureHandler failureHandler,
            CognitoLogoutSuccessHandler logoutSuccessHandler,
            JsonAuthenticationEntryPoint authenticationEntryPoint,
            JsonAccessDeniedHandler accessDeniedHandler,
            @Value("${springdoc.api-docs.enabled:false}") boolean swaggerEnabled
    ) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource))
                .csrf(csrf -> csrf
                        .csrfTokenRepository(csrfTokenRepository)
                        .csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler())
                )
                .securityContext(context -> context
                        .securityContextRepository(securityContextRepository)
                        .requireExplicitSave(true)
                )
                .sessionManagement(session -> session
                        .sessionCreationPolicy(SessionCreationPolicy.IF_REQUIRED)
                        .sessionFixation(fixation -> fixation.migrateSession())
                )
                .authorizeHttpRequests(authorize -> {
                    authorize.requestMatchers(
                            "/oauth2/**",
                            "/login/**",
                            "/error"
                    ).permitAll();
                    authorize.requestMatchers(
                            HttpMethod.GET,
                            "/api/auth/login",
                            "/actuator/health",
                            "/actuator/health/**"
                    ).permitAll();
                    if (swaggerEnabled) {
                        authorize.requestMatchers(
                                "/v3/api-docs/**",
                                "/swagger-ui/**",
                                "/swagger-ui.html"
                        ).permitAll();
                    }
                    authorize.requestMatchers("/api/admin/**").hasRole("ADMIN");
                    authorize.anyRequest().authenticated();
                })
                .oauth2Login(oauth2 -> oauth2
                        .authorizedClientRepository(authorizedClientRepository)
                        .userInfoEndpoint(userInfo -> userInfo
                                .userAuthoritiesMapper(authoritiesMapper)
                        )
                        .successHandler(successHandler)
                        .failureHandler(failureHandler)
                        .addObjectPostProcessor(
                                new ObjectPostProcessor<OAuth2LoginAuthenticationFilter>() {
                                    @Override
                                    public <O extends OAuth2LoginAuthenticationFilter> O postProcess(
                                            O filter
                                    ) {
                                        filter.setSecurityContextRepository(
                                                new RequestAttributeSecurityContextRepository()
                                        );
                                        return filter;
                                    }
                                }
                        )
                )
                .logout(logout -> logout
                        .logoutUrl("/api/auth/logout")
                        .invalidateHttpSession(true)
                        .clearAuthentication(true)
                        .deleteCookies("JSESSIONID", "XSRF-TOKEN")
                        .logoutSuccessHandler(logoutSuccessHandler)
                )
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(authenticationEntryPoint)
                        .accessDeniedHandler(accessDeniedHandler)
                )
                .formLogin(AbstractHttpConfigurer::disable)
                .httpBasic(AbstractHttpConfigurer::disable);

        return http.build();
    }
}
