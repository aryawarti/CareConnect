package com.careconnect.platform.logging;

import jakarta.servlet.Filter;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Auto-configuration: adding the dependency is enough — no @Import, no
 * component scan of another package (which would be a Spring anti-pattern).
 * Registered via META-INF/spring/...AutoConfiguration.imports.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformLoggingAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CorrelationIdFilter.class)
    Filter correlationIdFilter() {
        return new CorrelationIdFilter();
    }
}
