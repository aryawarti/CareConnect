package com.careconnect.queue.infrastructure.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.preauth.AbstractPreAuthenticatedProcessingFilter;

/**
 * Trusted-header model (ADR-005): the gateway has already validated the JWT
 * and forwards identity as X-User-Id / X-User-Roles; those headers cannot be
 * spoofed from outside because the gateway strips inbound copies. This filter
 * chain turns them into a SecurityContext for @PreAuthorize checks.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http,
                                            HeaderAuthenticationFilter headerAuth) throws Exception {
        return http
                .csrf(csrf -> csrf.disable())          // stateless API, no cookies for state
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        // Lobby display boards run on kiosks with no login.
                        // They expose token numbers only — no clinical data.
                        .requestMatchers("/api/queue/stream/**", "/api/queue/board/**").permitAll()
                        .requestMatchers("/actuator/health/**", "/actuator/info").permitAll()
                        .anyRequest().authenticated())
                // Anonymous → 401 (Spring's no-entry-point default is a misleading 403)
                .exceptionHandling(e -> e.authenticationEntryPoint((req, res, ex) -> {
                    res.setStatus(HttpStatus.UNAUTHORIZED.value());
                    res.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
                    res.getWriter().write("""
                            {"type":"https://careconnect.dev/errors/authentication",\
                            "title":"Authentication required","status":401}""");
                }))
                .addFilterBefore(headerAuth, AbstractPreAuthenticatedProcessingFilter.class)
                .build();
    }
}
