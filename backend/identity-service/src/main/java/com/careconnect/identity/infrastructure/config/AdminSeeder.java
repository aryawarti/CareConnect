package com.careconnect.identity.infrastructure.config;

import com.careconnect.identity.domain.Role;
import com.careconnect.identity.domain.User;
import com.careconnect.identity.infrastructure.repository.RoleRepository;
import com.careconnect.identity.infrastructure.repository.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

/**
 * Seeds the first ADMIN account, idempotently, from environment-provided
 * credentials — never a hash hard-coded in a migration (that would put a
 * crackable credential in git history). Disabled when no password is set.
 */
@Configuration
public class AdminSeeder {

    private static final Logger log = LoggerFactory.getLogger(AdminSeeder.class);

    @Bean
    CommandLineRunner seedAdmin(UserRepository users, RoleRepository roles,
                                PasswordEncoder encoder,
                                @Value("${careconnect.seed.admin-email:}") String email,
                                @Value("${careconnect.seed.admin-password:}") String password) {
        return args -> {
            if (email.isBlank() || password.isBlank()) {
                log.info("admin seed skipped (careconnect.seed.* not configured)");
                return;
            }
            if (users.existsByEmailIgnoreCase(email)) {
                return;
            }
            User admin = new User(email.toLowerCase(), encoder.encode(password));
            Role adminRole = roles.findByName(Role.ADMIN).orElseThrow();
            admin.addRole(adminRole);
            users.save(admin);
            log.info("seeded initial ADMIN account for {}", email);
        };
    }
}
