package com.careconnect.identity.api.dto;

import com.careconnect.identity.domain.Role;
import com.careconnect.identity.domain.User;
import java.util.List;
import java.util.UUID;

public record UserResponse(UUID id, String email, List<String> roles, String status) {

    public static UserResponse from(User user) {
        return new UserResponse(user.getId(), user.getEmail(),
                user.getRoles().stream().map(Role::getName).sorted().toList(),
                user.getStatus().name());
    }
}
