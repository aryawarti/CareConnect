package com.careconnect.identity.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.careconnect.identity.domain.Role;
import com.careconnect.identity.domain.User;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import java.lang.reflect.Field;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TokenServiceTest {

    private final TokenService service = new TokenService(
            new JwtProperties("test-secret-key-that-is-long-enough-for-hs256!!", 15, 7));

    private User userWithId() throws Exception {
        User user = new User("doc@careconnect.local", "hash");
        user.addRole(new Role(Role.DOCTOR));
        Field id = User.class.getDeclaredField("id");
        id.setAccessible(true);
        id.set(user, UUID.randomUUID());
        return user;
    }

    @Test
    void issuedAccessTokenRoundTrips() throws Exception {
        User user = userWithId();

        Claims claims = service.parse(service.issueAccessToken(user));

        assertThat(claims.getSubject()).isEqualTo(user.getId().toString());
        assertThat(service.roles(claims)).containsExactly("DOCTOR");
        assertThat(claims.getExpiration()).isInTheFuture();
    }

    @Test
    void tamperedTokenIsRejected() throws Exception {
        String token = service.issueAccessToken(userWithId());
        String tampered = token.substring(0, token.length() - 4) + "AAAA";

        assertThatThrownBy(() -> service.parse(tampered)).isInstanceOf(JwtException.class);
    }

    @Test
    void tokenSignedWithDifferentKeyIsRejected() throws Exception {
        TokenService other = new TokenService(
                new JwtProperties("another-secret-key-that-is-also-long-enough!!!!", 15, 7));
        String foreign = other.issueAccessToken(userWithId());

        assertThatThrownBy(() -> service.parse(foreign)).isInstanceOf(JwtException.class);
    }

    @Test
    void refreshTokensAreUniqueAndHashesAreStable() {
        String a = service.newOpaqueRefreshToken();
        String b = service.newOpaqueRefreshToken();

        assertThat(a).isNotEqualTo(b);
        assertThat(service.sha256(a)).isEqualTo(service.sha256(a)).hasSize(64);
    }
}
