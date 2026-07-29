package com.careconnect.laboratory.infrastructure.client;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Propagates the caller's identity (X-User-Id / X-User-Roles) and the
 * correlation id on every outbound Feign call. Downstream services authorize
 * with the ORIGINAL caller's identity — this service does not escalate
 * privileges on anyone's behalf.
 */
@Configuration
public class FeignConfig {

    private static final String[] FORWARDED = {"X-User-Id", "X-User-Roles", "X-Correlation-Id"};

    @Bean
    feign.RequestInterceptor identityForwardingInterceptor() {
        return template -> {
            if (RequestContextHolder.getRequestAttributes()
                    instanceof ServletRequestAttributes attrs) {
                HttpServletRequest request = attrs.getRequest();
                for (String header : FORWARDED) {
                    String value = request.getHeader(header);
                    if (value != null) {
                        template.header(header, value);
                    }
                }
            }
        };
    }
}
