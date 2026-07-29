package com.careconnect.identity.infrastructure.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Separate class on purpose: @EnableJpaAuditing on the application class
 * forces JPA into every context — including @WebMvcTest slices, which
 * exclude JPA and then fail with "JPA metamodel must not be empty".
 * Slice tests don't component-scan plain @Configuration classes, so
 * auditing loads only in full contexts. (Bug found by our own test suite.)
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
