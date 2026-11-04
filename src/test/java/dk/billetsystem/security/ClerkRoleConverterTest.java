package dk.billetsystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Kun brugere med role = "admin" i Clerks token får rollen ADMIN. */
class ClerkRoleConverterTest {

    private final ClerkRoleConverter converter = new ClerkRoleConverter();

    private static Jwt token(Object role) {
        Jwt.Builder builder = Jwt.withTokenValue("token").header("alg", "RS256").subject("user_123")
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60));
        if (role != null) builder.claim("role", role);
        return builder.build();
    }

    @Test
    void adminFårRollenAdmin() {
        AbstractAuthenticationToken auth = converter.convert(token("admin"));
        assertEquals("ROLE_ADMIN", auth.getAuthorities().iterator().next().getAuthority());
        assertEquals("user_123", auth.getName());
    }

    @Test
    void andreRollerFårIngenAdgang() {
        assertTrue(converter.convert(token("member")).getAuthorities().isEmpty());
        assertTrue(converter.convert(token("Admin ")).getAuthorities().isEmpty());
    }

    @Test
    void udenRolleFårIngenAdgang() {
        // Fx en bruger uden public metadata, eller hvis session-tokenet ikke er tilpasset i Clerk
        assertTrue(converter.convert(token(null)).getAuthorities().isEmpty());
    }
}
