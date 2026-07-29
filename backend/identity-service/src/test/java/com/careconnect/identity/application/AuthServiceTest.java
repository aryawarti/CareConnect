package com.careconnect.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.careconnect.identity.domain.AuthException;
import com.careconnect.identity.domain.RefreshToken;
import com.careconnect.identity.domain.Role;
import com.careconnect.identity.domain.User;
import com.careconnect.identity.infrastructure.repository.RefreshTokenRepository;
import com.careconnect.identity.infrastructure.repository.RoleRepository;
import com.careconnect.identity.infrastructure.repository.UserRepository;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock UserRepository users;
    @Mock RoleRepository roles;
    @Mock RefreshTokenRepository refreshTokens;

    private AuthService service;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder(4); // cheap for tests
    private final TokenService tokens = new TokenService(
            new JwtProperties("test-secret-key-that-is-long-enough-for-hs256!!", 15, 7));

    private User activeUser;

    @BeforeEach
    void setUp() throws Exception {
        service = new AuthService(users, roles, refreshTokens, encoder, tokens);
        activeUser = new User("pat@careconnect.local", encoder.encode("Password1!"));
        activeUser.addRole(new Role(Role.PATIENT));
        Field id = User.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(activeUser, UUID.randomUUID());
    }

    @Test
    void loginWithWrongPasswordFailsWithGenericMessage() {
        when(users.findByEmailIgnoreCase("pat@careconnect.local")).thenReturn(Optional.of(activeUser));

        assertThatThrownBy(() -> service.login("pat@careconnect.local", "wrong"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid email or password");   // same as unknown-user case
    }

    @Test
    void loginWithUnknownUserFailsWithSameMessage() {
        when(users.findByEmailIgnoreCase(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login("ghost@x.dev", "whatever"))
                .isInstanceOf(AuthException.class)
                .hasMessage("Invalid email or password");
    }

    @Test
    void successfulLoginIssuesTokenPairAndStoresOnlyHash() {
        when(users.findByEmailIgnoreCase("pat@careconnect.local")).thenReturn(Optional.of(activeUser));

        var auth = service.login("pat@careconnect.local", "Password1!");

        assertThat(auth.accessToken()).isNotBlank();
        assertThat(auth.roles()).containsExactly("PATIENT");
        verify(refreshTokens).save(org.mockito.ArgumentMatchers.argThat(t ->
                t.getTokenHash().length() == 64
                        && !t.getTokenHash().equals(auth.refreshToken())));
    }

    @Test
    void replayedRevokedRefreshTokenRevokesAllSessions() {
        UUID userId = UUID.randomUUID();
        RefreshToken revoked = new RefreshToken(userId, "hash", Instant.now().plusSeconds(3600));
        revoked.revoke();
        when(refreshTokens.findByTokenHash(any())).thenReturn(Optional.of(revoked));

        assertThatThrownBy(() -> service.refresh("stolen-token"))
                .isInstanceOf(AuthException.class);
        verify(refreshTokens).revokeAllForUser(userId);
    }
}
