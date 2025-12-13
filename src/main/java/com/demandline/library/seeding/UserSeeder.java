package com.demandline.library.seeding;

import com.demandline.library.repository.RoleRepository;
import com.demandline.library.repository.UserRepository;
import com.demandline.library.repository.model.UserEntity;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * User Seeder
 * Seeds default users into the database on application startup
 * Only creates users if they don't already exist
 */
@Component
@Order(1)
@Slf4j
public class UserSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public UserSeeder(UserRepository userRepository,
                     RoleRepository roleRepository,
                     PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        log.info("Starting user seeding...");

        seedAdminUser();
        seedLibrarianUser();
        seedFrontDeskUser();

        log.info("User seeding completed.");
    }

    private void seedAdminUser() {
        String email = "admin@library.local";

        if (userRepository.findByEmail(email).isPresent()) {
            log.info("Admin user already exists, skipping: {}", email);
            return;
        }

        var adminRole = roleRepository.findByName("ADMIN")
                .orElseThrow(() -> new IllegalStateException("ADMIN role not found. Ensure migrations have run."));

        var admin = UserEntity.builder()
                .name("Administrator")
                .email(email)
                .password(passwordEncoder.encode("admin123"))
                .roleEntity(adminRole)
                .active(true)
                .build();

        userRepository.save(admin);
        log.info("Admin user seeded successfully: {}", email);
    }

    private void seedLibrarianUser() {
        String email = "librarian@library.local";

        if (userRepository.findByEmail(email).isPresent()) {
            log.info("Librarian user already exists, skipping: {}", email);
            return;
        }

        var librarianRole = roleRepository.findByName("LIBRARIAN")
                .orElseThrow(() -> new IllegalStateException("LIBRARIAN role not found. Ensure migrations have run."));

        var librarian = UserEntity.builder()
                .name("Sample Librarian")
                .email(email)
                .password(passwordEncoder.encode("librarian123"))
                .roleEntity(librarianRole)
                .active(true)
                .build();

        userRepository.save(librarian);
        log.info("Librarian user seeded successfully: {}", email);
    }

    private void seedFrontDeskUser() {
        String email = "frontdesk@library.local";

        if (userRepository.findByEmail(email).isPresent()) {
            log.info("Front Desk user already exists, skipping: {}", email);
            return;
        }

        var frontDeskRole = roleRepository.findByName("FRONT_DESK_STAFF")
                .orElseThrow(() -> new IllegalStateException("FRONT_DESK_STAFF role not found. Ensure migrations have run."));

        var frontDesk = UserEntity.builder()
                .name("Sample Front Desk Staff")
                .email(email)
                .password(passwordEncoder.encode("frontdesk123"))
                .roleEntity(frontDeskRole)
                .active(true)
                .build();

        userRepository.save(frontDesk);
        log.info("Front Desk user seeded successfully: {}", email);
    }
}

