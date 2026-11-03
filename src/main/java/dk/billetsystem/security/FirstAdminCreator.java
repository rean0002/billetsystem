package dk.billetsystem.security;

import dk.billetsystem.model.AdminUser;
import dk.billetsystem.repository.AdminUserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Opretter den første admin, når programmet starter, hvis der ikke findes nogen endnu.
 *
 * Brugernavn og kodeord læses fra miljøvariablerne ADMIN_USERNAME og ADMIN_PASSWORD,
 * så kodeordet aldrig står i koden. Findes der allerede en admin, gør klassen ingenting,
 * så det er ufarligt at lade variablerne stå.
 */
@Component
public class FirstAdminCreator implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(FirstAdminCreator.class);
    private static final int MIN_PASSWORD_LENGTH = 10;

    private final AdminUserRepository adminUserRepository;
    private final PasswordEncoder passwordEncoder;
    private final String username;
    private final String password;

    // Spring læser ADMIN_USERNAME som admin.username og ADMIN_PASSWORD som admin.password
    public FirstAdminCreator(AdminUserRepository adminUserRepository,
                             PasswordEncoder passwordEncoder,
                             @Value("${admin.username:admin}") String username,
                             @Value("${admin.password:}") String password) {
        this.adminUserRepository = adminUserRepository;
        this.passwordEncoder = passwordEncoder;
        this.username = username;
        this.password = password;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminUserRepository.count() > 0) {
            return;
        }
        if (password.isBlank()) {
            log.warn("Der findes ingen admin. Sæt ADMIN_PASSWORD (og evt. ADMIN_USERNAME) og genstart for at oprette en.");
            return;
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            log.warn("ADMIN_PASSWORD skal være mindst {} tegn. Ingen admin oprettet.", MIN_PASSWORD_LENGTH);
            return;
        }

        adminUserRepository.save(new AdminUser(username, passwordEncoder.encode(password)));
        log.info("Oprettede den første admin: '{}'", username);
    }
}
