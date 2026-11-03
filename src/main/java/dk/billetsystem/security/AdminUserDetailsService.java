package dk.billetsystem.security;

import dk.billetsystem.repository.AdminUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Fortæller Spring Security, hvor brugerne findes: i tabellen admin_user.
 *
 * Når nogen logger ind, kalder Spring denne klasse med brugernavnet, får hashen tilbage
 * og tjekker selv, om det indtastede kodeord passer til den. Vi rører aldrig selve kodeordet.
 */
@Service
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminUserRepository adminUserRepository;

    public AdminUserDetailsService(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return adminUserRepository.findByUsername(username)
                .map(admin -> User.withUsername(admin.getUsername())
                        .password(admin.getPasswordHash())
                        .roles("ADMIN")
                        .build())
                // Beskeden vises ikke til brugeren. Login-siden siger bare "forkert brugernavn eller kodeord",
                // så man ikke kan gætte sig frem til, hvilke brugernavne der findes.
                .orElseThrow(() -> new UsernameNotFoundException("Ukendt bruger: " + username));
    }
}
