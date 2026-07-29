package com.careconnect.identity.api;

import com.careconnect.identity.api.dto.ApiEnvelope;
import com.careconnect.identity.api.dto.AuthResponse;
import com.careconnect.identity.api.dto.LoginRequest;
import com.careconnect.identity.api.dto.RefreshRequest;
import com.careconnect.identity.api.dto.RegisterRequest;
import com.careconnect.identity.application.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Public auth endpoints (allowlisted at the gateway — everything else
 * requires a valid JWT before it ever reaches a service).
 */
@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/register")
    public ResponseEntity<ApiEnvelope<AuthResponse>> register(@Valid @RequestBody RegisterRequest request) {
        AuthResponse auth = authService.register(request.email(), request.password(),
                request.roleOrDefault());
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiEnvelope.of(auth));
    }

    @PostMapping("/login")
    public ApiEnvelope<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ApiEnvelope.of(authService.login(request.email(), request.password()));
    }

    @PostMapping("/refresh")
    public ApiEnvelope<AuthResponse> refresh(@Valid @RequestBody RefreshRequest request) {
        return ApiEnvelope.of(authService.refresh(request.refreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshRequest request) {
        authService.logout(request.refreshToken());
        return ResponseEntity.noContent().build();
    }
}
