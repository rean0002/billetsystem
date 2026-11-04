package dk.billetsystem.security;

import org.springframework.core.convert.converter.Converter;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;

import java.util.List;

/**
 * Oversætter Clerks token til Spring Securitys roller.
 *
 * I Clerk-dashboardet får arrangørerne public metadata {"role": "admin"}, og session-tokenet er tilpasset,
 * så det indeholder {"role": "{{user.public_metadata.role}}"}. Står der "admin", får brugeren rollen ADMIN.
 * Alle andre (fx hvis nogen alligevel opretter en konto) er logget ind, men får 403 på admin-API'et.
 */
public class ClerkRoleConverter implements Converter<Jwt, AbstractAuthenticationToken> {

    @Override
    public AbstractAuthenticationToken convert(Jwt jwt) {
        List<GrantedAuthority> authorities = "admin".equals(jwt.getClaimAsString("role"))
                ? List.of(new SimpleGrantedAuthority("ROLE_ADMIN"))
                : List.of();
        return new JwtAuthenticationToken(jwt, authorities, jwt.getSubject());
    }
}
