package com.careconnect.provider.infrastructure.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/** Builds the SecurityContext from gateway-forwarded identity headers. */
@Component
public class HeaderAuthenticationFilter extends OncePerRequestFilter {

    public static final String USER_ID_HEADER = "X-User-Id";
    public static final String ROLES_HEADER = "X-User-Roles";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String userId = request.getHeader(USER_ID_HEADER);
        String roles = request.getHeader(ROLES_HEADER);
        if (userId != null && !userId.isBlank()) {
            List<SimpleGrantedAuthority> authorities = roles == null ? List.of()
                    : Arrays.stream(roles.split(","))
                            .filter(r -> !r.isBlank())
                            .map(r -> new SimpleGrantedAuthority("ROLE_" + r.trim()))
                            .toList();
            SecurityContextHolder.getContext().setAuthentication(
                    new UsernamePasswordAuthenticationToken(userId, null, authorities));
        }
        chain.doFilter(request, response);
    }
}
