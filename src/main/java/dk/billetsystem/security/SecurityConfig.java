package dk.billetsystem.security;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.HttpStatusEntryPoint;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;

/**
 * Adgangsreglerne for hele programmet.
 *
 * Princippet er "lukket, medmindre andet er sagt": vi skriver præcis, hvad besøgende må,
 * og alt andet kræver, at man er logget ind som admin.
 */
@Configuration
public class SecurityConfig {

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
                .authorizeHttpRequests(auth -> auth
                        // Bestillingssiden og dens filer
                        .requestMatchers("/", "/index.html", "/css/**", "/js/**", "/fonts/**", "/favicon.ico").permitAll()
                        // Det offentlige API
                        .requestMatchers(HttpMethod.GET, "/api/ticket-types/**", "/api/csrf").permitAll()
                        .requestMatchers(HttpMethod.POST, "/api/orders").permitAll()
                        // Login-siden skal man kunne se uden at være logget ind
                        .requestMatchers("/admin/login.html").permitAll()
                        // Springs egen fejlside, så fejl ikke bliver sendt videre til login
                        .requestMatchers("/error").permitAll()
                        // Alt andet: kun admins
                        .anyRequest().hasRole("ADMIN"))

                .formLogin(form -> form
                        .loginPage("/admin/login.html")       // vores egen login-side
                        .loginProcessingUrl("/admin/login")   // formularen sendes hertil, Spring tjekker kodeordet
                        .defaultSuccessUrl("/admin/index.html", true)
                        .failureUrl("/admin/login.html?error")
                        .permitAll())

                .logout(logout -> logout
                        .logoutUrl("/admin/logout")
                        .logoutSuccessUrl("/admin/login.html?logout"))

                // CSRF-beskyttelse forhindrer andre hjemmesider i at sende formularer i en indlogget admins navn.
                // POST /api/orders er offentlig og bruger ikke login, så den behøver ikke beskyttelsen.
                .csrf(csrf -> csrf
                        .ignoringRequestMatchers(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/orders")))

                // Kalder man /api/... uden at være logget ind, får man 401 i stedet for at blive sendt til login-siden.
                // Det er nemmere at håndtere i JavaScript.
                .exceptionHandling(e -> e.defaultAuthenticationEntryPointFor(
                        new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED),
                        PathPatternRequestMatcher.withDefaults().matcher("/api/**")));

        return http.build();
    }

    /** BCrypt laver en ny, tilfældig "salt" for hvert kodeord, så to ens kodeord får forskellige hashes. */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
