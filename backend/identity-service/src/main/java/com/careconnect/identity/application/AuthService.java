package com.careconnect.identity.application;

import com.careconnect.identity.api.dto.AuthResponse;
import com.careconnect.identity.domain.AuthException;
import com.careconnect.identity.domain.EmailAlreadyUsedException;
import com.careconnect.identity.domain.RefreshToken;
import com.careconnect.identity.domain.Role;
import com.careconnect.identity.domain.User;
import com.careconnect.identity.infrastructure.repository.RefreshTokenRepository;
import com.careconnect.identity.infrastructure.repository.RoleRepository;
import com.careconnect.identity.infrastructure.repository.UserRepository;
import java.time.Instant;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final RefreshTokenRepository refreshTokens;
    private final PasswordEncoder passwordEncoder;
    private final TokenService tokens;

    public AuthService(UserRepository users, RoleRepository roles,
                       RefreshTokenRepository refreshTokens,
                       PasswordEncoder passwordEncoder, TokenService tokens) {
        this.users = users;
        this.roles = roles;
        this.refreshTokens = refreshTokens;
        this.passwordEncoder = passwordEncoder;
        this.tokens = tokens;
    }

    /**
     * Self-registration. A visitor may sign up as a PATIENT or apply as a
     * DOCTOR; STAFF and ADMIN are granted only by an administrator (FR-A1).
     * A self-registered doctor still has to be verified by the hospital before
     * patients can see or book them.
     */
    @Transactional
    public AuthResponse register(String email, String rawPassword, String requestedRole) {
        if (users.existsByEmailIgnoreCase(email)) {
            throw new EmailAlreadyUsedException();
        }
        String roleName = "DOCTOR".equalsIgnoreCase(requestedRole) ? Role.DOCTOR : Role.PATIENT;
        User user = new User(email.toLowerCase(), passwordEncoder.encode(rawPassword));
        Role patientRole = roles.findByName(roleName)
                .orElseThrow(() -> new IllegalStateException(roleName + " role missing — was Flyway seed applied?"));
        user.addRole(patientRole);
        users.save(user);
        log.info("user registered id={} role={}", user.getId(), roleName);
        return issuePair(user);
    }

    @Transactional
    public AuthResponse login(String email, String rawPassword) {
        User user = users.findByEmailIgnoreCase(email)
                // Same error whether the user is missing or the password is wrong:
                // login must not be a user-enumeration oracle.
                .orElseThrow(() -> new AuthException("Invalid email or password"));
        if (!passwordEncoder.matches(rawPassword, user.getPasswordHash())) {
            throw new AuthException("Invalid email or password");
        }
        if (!user.isActive()) {
            throw new AuthException("Account is not active");
        }
        log.info("user login id={}", user.getId());
        return issuePair(user);
    }

    /**
     * Refresh-token rotation: each refresh consumes the presented token and
     * issues a new one. A replayed (already-revoked) token is treated as
     * evidence of theft — every session for that user is revoked.
     */
    @Transactional
    public AuthResponse refresh(String rawRefreshToken) {
        RefreshToken stored = refreshTokens.findByTokenHash(tokens.sha256(rawRefreshToken))
                .orElseThrow(() -> new AuthException("Invalid refresh token"));
        if (stored.isRevoked()) {
            int n = refreshTokens.revokeAllForUser(stored.getUserId());
            log.warn("revoked refresh token replayed — revoking all {} sessions for user {}",
                    n, stored.getUserId());
            throw new AuthException("Invalid refresh token");
        }
        if (!stored.isUsable()) {
            throw new AuthException("Refresh token expired");
        }
        stored.revoke();
        User user = users.findById(stored.getUserId())
                .filter(User::isActive)
                .orElseThrow(() -> new AuthException("Account is not active"));
        return issuePair(user);
    }

    @Transactional
    public void logout(String rawRefreshToken) {
        refreshTokens.findByTokenHash(tokens.sha256(rawRefreshToken))
                .ifPresent(RefreshToken::revoke);
        // Silent when unknown: logout is idempotent and never errors.
    }

    @Transactional
    public void logoutEverywhere(UUID userId) {
        refreshTokens.revokeAllForUser(userId);
    }

    private AuthResponse issuePair(User user) {
        String access = tokens.issueAccessToken(user);
        String refresh = tokens.newOpaqueRefreshToken();
        refreshTokens.save(new RefreshToken(
                user.getId(), tokens.sha256(refresh),
                Instant.now().plus(tokens.refreshTtl())));
        return new AuthResponse(access, refresh, user.getId(), user.getEmail(),
                user.getRoles().stream().map(Role::getName).sorted().toList());
    }
}
