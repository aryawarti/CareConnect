package com.careconnect.platform.security;

import com.careconnect.platform.client.IdentityForwardingInterceptor;
import feign.RequestInterceptor;
import jakarta.servlet.Filter;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Wires {@link GatewayTrustFilter} into every service that depends on this
 * starter, so the "only the gateway may assert an identity" rule is implemented
 * once instead of eight times slightly differently.
 *
 * The secret is empty by default, which disables the check. That is deliberate:
 * slice tests (@WebMvcTest) and integration tests set identity headers directly
 * and must keep working without knowing this filter exists. Any environment
 * where the gateway is real sets CARECONNECT_GATEWAY_SECRET, and the absence of
 * it is logged loudly at startup so a misconfigured deployment is obvious.
 */
@AutoConfiguration
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class PlatformSecurityAutoConfiguration {

    private static final Logger log =
            LoggerFactory.getLogger(PlatformSecurityAutoConfiguration.class);

    @Bean
    @ConditionalOnMissingBean(GatewayTrustFilter.class)
    Filter gatewayTrustFilter(
            @Value("${careconnect.platform.gateway-secret:}") String sharedSecret) {
        if (sharedSecret == null || sharedSecret.isBlank()) {
            log.warn("careconnect.platform.gateway-secret is not set — this service will "
                    + "trust X-User-* headers from ANY caller. Acceptable for tests and "
                    + "single-host local dev; never for a deployment.");
        } else {
            log.info("gateway trust enforced: identity headers require a valid {}",
                    GatewayTrustFilter.GATEWAY_AUTH_HEADER);
        }
        return new GatewayTrustFilter(sharedSecret);
    }

    /**
     * Only for services that actually call others. Nested and
     * {@code @ConditionalOnClass} so services without OpenFeign on the
     * classpath (notification, identity) are unaffected.
     */
    @Configuration(proxyBeanMethods = false)
    @ConditionalOnClass(RequestInterceptor.class)
    static class FeignIdentityForwarding {

        @Bean
        @ConditionalOnMissingBean(IdentityForwardingInterceptor.class)
        RequestInterceptor identityForwardingInterceptor(
                @Value("${careconnect.platform.gateway-secret:}") String sharedSecret) {
            return new IdentityForwardingInterceptor(sharedSecret);
        }
    }
}
