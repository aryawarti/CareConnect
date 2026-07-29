package com.careconnect.identity.api;

import com.careconnect.identity.api.dto.ApiEnvelope;
import com.careconnect.identity.api.dto.CreateUserRequest;
import com.careconnect.identity.api.dto.ResetPasswordRequest;
import com.careconnect.identity.api.dto.UserResponse;
import com.careconnect.identity.api.dto.UserSummary;
import java.util.List;
import java.util.UUID;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import com.careconnect.identity.application.UserService;
import jakarta.validation.Valid;
import java.net.URI;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Staff directory — every account with access to the hospital's systems. */
    @GetMapping
    @PreAuthorize("hasAnyRole('ADMIN','STAFF')")
    public ApiEnvelope<List<UserSummary>> list(@PageableDefault(size = 50) Pageable pageable) {
        return ApiEnvelope.ofPage(userService.list(pageable), UserSummary::from);
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiEnvelope<UserSummary> deactivate(@PathVariable UUID id) {
        return ApiEnvelope.of(UserSummary.from(userService.setActive(id, false)));
    }

    @PostMapping("/{id}/activate")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiEnvelope<UserSummary> activate(@PathVariable UUID id) {
        return ApiEnvelope.of(UserSummary.from(userService.setActive(id, true)));
    }

    @PostMapping("/{id}/password")
    @PreAuthorize("hasRole('ADMIN')")
    public ApiEnvelope<UserSummary> resetPassword(@PathVariable UUID id,
                                                  @Valid @RequestBody ResetPasswordRequest request) {
        return ApiEnvelope.of(UserSummary.from(
                userService.resetPassword(id, request.newPassword())));
    }

    @PostMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiEnvelope<UserResponse>> create(
            @Valid @RequestBody CreateUserRequest request) {
        UserResponse created = UserResponse.from(userService.create(request));
        return ResponseEntity.created(URI.create("/api/users/" + created.id()))
                .body(ApiEnvelope.of(created));
    }
}
