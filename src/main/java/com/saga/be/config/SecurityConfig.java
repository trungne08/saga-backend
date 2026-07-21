package com.saga.be.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            // Tắt CSRF vì REST API thường là Stateless (không dùng Session)
            .csrf(AbstractHttpConfigurer::disable)
            // Kích hoạt CORS (sẽ tự động dùng cấu hình CorsConfig đã định nghĩa trước đó)
            .cors(Customizer.withDefaults())
            // Cấu hình phân quyền đường dẫn
            .authorizeHttpRequests(auth -> auth
                // Bỏ qua xác thực cho các endpoint của Swagger và OpenAPI Docs
                .requestMatchers(
                    "/v3/api-docs/**",
                    "/swagger-ui/**",
                    "/swagger-ui.html"
                ).permitAll()
                // Bỏ qua xác thực cho Webhook (Ví dụ: Cognito Post-Confirmation Trigger)
                .requestMatchers("/api/v1/webhooks/cognito/**").permitAll()
                // Tất cả các request khác (bao gồm các API hệ thống SAGA) đều phải có Token hợp lệ
                .anyRequest().authenticated()
            )
            // Cấu hình ứng dụng thành OAuth2 Resource Server để xử lý JWT
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(Customizer.withDefaults())
            );

        return http.build();
    }
}