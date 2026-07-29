package com.careconnect.identity.application;

import com.careconnect.identity.api.dto.CreateUserRequest;
import com.careconnect.identity.domain.EmailAlreadyUsedException;
import com.careconnect.identity.domain.User;
import com.careconnect.identity.domain.UserStatus;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import com.careconnect.identity.infrastructure.repository.RoleRepository;
import com.careconnect.identity.infrastructure.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Admin-side account provisioning (FR-A1): staff/doctor/admin accounts. */
@Service
public class UserService {

    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    private final UserRepository users;
    private final RoleRepository roles;
    private final PasswordEncoder encoder;
    private final com.careconnect.identity.infrastructure.repository.RefreshTokenRepository refreshTokens;

    public UserService(UserRepository users, RoleRepository roles, PasswordEncoder encoder,
                       com.careconnect.identity.infrastructure.repository.RefreshTokenRepository refreshTokens) {
        this.users = users;
        this.roles = roles;
        this.encoder = encoder;
        this.refreshTokens = refreshTokens;
    }

    @Transactional(readOnly = true)
    public Page<User> list(Pageable pageable) {
        return users.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public User get(UUID id) {
        return users.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("No such user"));
    }

    /**
     * Deactivation rather than deletion: a departed doctor's name still appears
     * on every chart they signed, so the account must remain resolvable — it
     * simply stops being able to log in.
     */
    @Transactional
    public User setActive(UUID id, boolean active) {
        User user = get(id);
        user.setStatus(active ? UserStatus.ACTIVE : UserStatus.DISABLED);
        refreshTokens.revokeAllForUser(id);   // disabling ends existing sessions
        log.info("user {} set to {}", id, user.getStatus());
        return user;
    }

    /** Admin-issued temporary password (e.g. a doctor locked out on a ward). */
    @Transactional
    public User resetPassword(UUID id, String newPassword) {
        User user = get(id);
        user.setPasswordHash(encoder.encode(newPassword));
        refreshTokens.revokeAllForUser(id);
        log.info("password reset for user {}", id);
        return user;
    }

    @Transactional
    public User create(CreateUserRequest request) {
        if (users.existsByEmailIgnoreCase(request.email())) {
            throw new EmailAlreadyUsedException();
        }
        User user = new User(request.email().toLowerCase(), encoder.encode(request.password()));
        request.roles().forEach(name -> user.addRole(
                roles.findByName(name.toUpperCase()).orElseThrow(
                        () -> new IllegalArgumentException("Unknown role: " + name))));
        users.save(user);
        log.info("user provisioned id={} roles={}", user.getId(), request.roles());
        return user;
    }
}
