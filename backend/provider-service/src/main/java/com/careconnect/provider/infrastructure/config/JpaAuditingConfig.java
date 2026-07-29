package com.careconnect.provider.infrastructure.config;

import java.util.Optional;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.domain.AuditorAware;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;
import org.springframework.security.core.context.SecurityContextHolder;

/**
 * Separate class on purpose: @EnableJpaAuditing on the application class
 * forces JPA into every context — including @WebMvcTest slices, which
 * exclude JPA and then fail with "JPA metamodel must not be empty".
 * Slice tests don't component-scan plain @Configuration classes, so
 * auditing loads only in full contexts. (Bug found by our own test suite.)
 */
@Configuration
@EnableJpaAuditing(auditorAwareRef = "auditorAware")
public class JpaAuditingConfig {

    /** created_by/updated_by = acting userId from the gateway-trusted headers. */
    @Bean
    AuditorAware<String> auditorAware() {
        return () -> Optional.ofNullable(SecurityContextHolder.getContext().getAuthentication())
                .map(auth -> String.valueOf(auth.getPrincipal()))
                .filter(p -> !"anonymousUser".equals(p));
    }
}
