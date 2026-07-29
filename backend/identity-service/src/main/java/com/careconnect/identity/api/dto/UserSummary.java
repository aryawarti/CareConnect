package com.careconnect.identity.api.dto;

import com.careconnect.identity.domain.Role;
import com.careconnect.identity.domain.User;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Staff directory row: who has access to this hospital's systems, and as what. */
public record UserSummary(UUID id, String email, List<String> roles,
                          String status, Instant createdAt) {

    public static UserSummary from(User user) {
        return new UserSummary(user.getId(), user.getEmail(),
                user.getRoles().stream().map(Role::getName).sorted().toList(),
                user.getStatus().name(), user.getCreatedAt());
    }
}
