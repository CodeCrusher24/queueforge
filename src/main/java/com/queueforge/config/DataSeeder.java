package com.queueforge.config;

import com.queueforge.security.Role;
import com.queueforge.user.User;
import com.queueforge.user.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
@Profile("local")
public class DataSeeder implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public DataSeeder(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) {
        if (userRepository.count() == 0) {
            User admin = new User();
            admin.setEmail("admin@queueforge.local");
            admin.setPasswordHash(passwordEncoder.encode("admin123"));
            admin.setDisplayName("Admin User");
            admin.setRole(Role.ADMIN);
            userRepository.save(admin);

            User dev = new User();
            dev.setEmail("dev@queueforge.local");
            dev.setPasswordHash(passwordEncoder.encode("dev123"));
            dev.setDisplayName("Developer User");
            dev.setRole(Role.DEVELOPER);
            userRepository.save(dev);
        }
    }
}