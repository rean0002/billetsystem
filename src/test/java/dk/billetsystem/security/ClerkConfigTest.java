package dk.billetsystem.security;

import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Aflæsning af Clerks nøgle og tjek af tokenets udsteder (iss), udløb og domæne (azp). */
class ClerkConfigTest {

    private static final String ISSUER = "https://noble-fox-12.clerk.accounts.dev";
    private final OAuth2TokenValidator<Jwt> validator = ClerkConfig.validator(ISSUER, List.of("http://localhost:8081"));

    private static String key(String prefix, String frontendApi) {
        return prefix + Base64.getEncoder().encodeToString((frontendApi + "$").getBytes(StandardCharsets.UTF_8));
    }

    private static Jwt.Builder token() {
        Instant now = Instant.now();
        return Jwt.withTokenValue("token").header("alg", "RS256")
                .issuer(ISSUER).subject("user_123").issuedAt(now).expiresAt(now.plusSeconds(60))
                .claim("azp", "http://localhost:8081");
    }

    @Test
    void frontendApiLæsesUdAfNøglen() {
        assertEquals("noble-fox-12.clerk.accounts.dev", ClerkConfig.frontendApi(key("pk_test_", "noble-fox-12.clerk.accounts.dev")));
        assertEquals("clerk.example.com", ClerkConfig.frontendApi(key("pk_live_", "clerk.example.com")));
    }

    @Test
    void nøgleUdenPaddingVirkerOgså() {
        // Clerks nøgler har normalt ikke "=" til sidst, ligesom denne (opdigtede) nøgle
        assertEquals("noble-fox-12.clerk.accounts.dev",
                ClerkConfig.frontendApi("pk_test_bm9ibGUtZm94LTEyLmNsZXJrLmFjY291bnRzLmRldiQ"));
    }

    @Test
    void ugyldigNøgleGiverForståeligFejl() {
        IllegalStateException e = assertThrows(IllegalStateException.class, () -> ClerkConfig.frontendApi("sk_test_hemmelig"));
        assertTrue(e.getMessage().contains("CLERK_PUBLISHABLE_KEY"));
    }

    @Test
    void gyldigtTokenGodkendes() {
        assertFalse(validator.validate(token().build()).hasErrors());
    }

    @Test
    void tokenFraEtAndetDomæneAfvises() {
        assertTrue(validator.validate(token().claim("azp", "https://en-anden-side.example.com").build()).hasErrors());
    }

    @Test
    void tokenUdenAzpAfvises() {
        Jwt jwt = Jwt.withTokenValue("token").header("alg", "RS256").issuer(ISSUER)
                .issuedAt(Instant.now()).expiresAt(Instant.now().plusSeconds(60)).build();
        assertTrue(validator.validate(jwt).hasErrors());
    }

    @Test
    void tokenFraEnAndenClerkAppAfvises() {
        assertTrue(validator.validate(token().issuer("https://fremmed-app.clerk.accounts.dev").build()).hasErrors());
    }

    @Test
    void udløbetTokenAfvises() {
        Instant past = Instant.now().minusSeconds(600);
        assertTrue(validator.validate(token().issuedAt(past.minusSeconds(60)).expiresAt(past).build()).hasErrors());
    }
}
