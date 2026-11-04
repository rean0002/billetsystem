package dk.billetsystem.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.BadJwtException;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Base64;
import java.util.List;

/**
 * Opsætning af login via Clerk (clerk.com).
 *
 * Clerk står for selve login (kodeord, spærring ved gætteri osv.). Når en arrangør er logget ind, giver Clerk
 * browseren et kortlivet token (en JWT), som adminsiden sender med hvert kald i headeren "Authorization: Bearer ...".
 * Her tjekker vi, at tokenet er underskrevet af netop vores Clerk-app (med Clerks offentlige nøgler),
 * ikke er udløbet og er lavet til vores eget domæne.
 */
@Configuration
public class ClerkConfig {

    private static final Logger log = LoggerFactory.getLogger(ClerkConfig.class);

    private final String publishableKey;
    private final List<String> authorizedParties;

    public ClerkConfig(@Value("${clerk.publishable-key:}") String publishableKey,
                       @Value("${clerk.authorized-parties:}") String authorizedParties) {
        this.publishableKey = publishableKey.trim();
        this.authorizedParties = Arrays.stream(authorizedParties.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /** Den offentlige nøgle, som login-siden skal bruge for at vise Clerks login. */
    public String publishableKey() {
        return publishableKey;
    }

    @Bean
    public JwtDecoder jwtDecoder() {
        if (publishableKey.isEmpty()) {
            log.warn("CLERK_PUBLISHABLE_KEY er ikke sat, så ingen kan logge ind på adminsiden");
            return token -> {
                throw new BadJwtException("Clerk er ikke sat op (CLERK_PUBLISHABLE_KEY mangler)");
            };
        }
        String issuer = "https://" + frontendApi(publishableKey);
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(issuer + "/.well-known/jwks.json").build();
        decoder.setJwtValidator(validator(issuer, authorizedParties));
        return decoder;
    }

    /**
     * Tjekker udløbstid, at tokenet kommer fra vores Clerk-app (iss), og at det er lavet til et af vores
     * egne domæner (azp). Uden azp-tjekket kunne et token fra en anden side, der bruger samme Clerk-app, bruges her.
     */
    static OAuth2TokenValidator<Jwt> validator(String issuer, List<String> authorizedParties) {
        return new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(issuer),
                new JwtClaimValidator<String>("azp", azp -> azp != null && authorizedParties.contains(azp)));
    }

    /**
     * Clerks publishable key indeholder adressen på jeres Clerk-app, fx
     * "pk_test_" + base64("noble-fox-12.clerk.accounts.dev$") -> "noble-fox-12.clerk.accounts.dev".
     */
    static String frontendApi(String publishableKey) {
        String[] parts = publishableKey.split("_");
        if (parts.length != 3 || !parts[0].equals("pk")) {
            throw new IllegalStateException("CLERK_PUBLISHABLE_KEY ser ikke ud som en Clerk-nøgle (pk_test_... eller pk_live_...)");
        }
        String decoded = new String(Base64.getDecoder().decode(parts[2]), StandardCharsets.UTF_8);
        return decoded.endsWith("$") ? decoded.substring(0, decoded.length() - 1) : decoded;
    }
}
