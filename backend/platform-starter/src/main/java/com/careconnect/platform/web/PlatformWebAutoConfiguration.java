package com.careconnect.platform.web;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;

/**
 * Response conventions every service shares.
 *
 * Servlet-only by condition, which also keeps it away from the reactive
 * api-gateway: the exception types {@link RequestErrorAdvice} handles are
 * Spring MVC's, and the gateway has its own error contract.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformWebAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(RequestErrorAdvice.class)
    public RequestErrorAdvice requestErrorAdvice() {
        return new RequestErrorAdvice();
    }
}
