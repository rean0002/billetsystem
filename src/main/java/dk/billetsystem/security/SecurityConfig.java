package dk.billetsystem.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

import java.time.Clock;

/**
 * Adgangsreglerne for hele programmet.
 *
 * Princippet er "lukket, medmindre andet er sagt": vi skriver præcis, hvad besøgende må,
 * og alt andet afvises. Admin-API'et kræver et gyldigt token fra Clerk med rollen admin (se ClerkConfig).
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Bestillingssiden og dens filer
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/fonts/**", "/favicon.ico").permitAll()
                        // Adminsidernes HTML og JavaScript indeholder ingen data, så de må hentes uden login.
                        // Alle data hentes via /api/admin, som kræver login (og uden login sendes man til login-siden).
                        .requestMatchers("/admin", "/admin/", "/admin/**").permitAll()
                        // Det offentlige API
                        .requestMatchers(HttpMethod.GET, "/api/ticket-types/**", "/api/clerk-config").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                        // Springs egen fejlside
                        .requestMatchers("/error").permitAll()
                        // Admin-API'et: kun arrangører med rollen admin i Clerk
                        .requestMatchers("/api/admin/**").hasRole("ADMIN")
                        // Alt andet afvises
                        .anyRequest().denyAll())

                // Tjek Clerks token i "Authorization: Bearer ..." (uden token: 401, uden admin-rolle: 403)
                .oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(new ClerkRoleConverter())))

                // Serveren husker ikke, hvem der er logget ind (ingen session-cookie). Hvert kald har sit eget token.
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))

                // CSRF-beskyttelse er ikke nødvendig: admin-API'et bruger et token i en header, som en anden
                // hjemmeside ikke kan få browseren til at sende med (i modsætning til en cookie).
                .csrf(AbstractHttpConfigurer::disable);

        return http.build();
    }

    /** Uret, fx OrderRateLimiter og UnpaidOrderExpiryJob bruger. (I testene bruges et ur, man selv kan stille frem.) */
    @Bean
    public Clock clock() {
        return Clock.systemUTC();
    }
}
