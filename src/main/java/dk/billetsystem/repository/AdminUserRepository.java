package dk.billetsystem.repository;

import dk.billetsystem.model.AdminUser;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

/**
 * Opslag af adminbrugere. Spring laver selv SQL'en ud fra metodenavnet:
 * findByUsername -> select ... where username = ?
 */
public interface AdminUserRepository extends JpaRepository<AdminUser, Integer> {

    Optional<AdminUser> findByUsername(String username);
}
