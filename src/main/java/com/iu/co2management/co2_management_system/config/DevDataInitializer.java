package com.iu.co2management.co2_management_system.config;

import com.iu.co2management.co2_management_system.entity.AppUser;
import com.iu.co2management.co2_management_system.entity.Location;
import com.iu.co2management.co2_management_system.entity.Role;
import com.iu.co2management.co2_management_system.repository.AppUserRepository;
import com.iu.co2management.co2_management_system.repository.LocationRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

@Configuration
public class DevDataInitializer {

    @Bean
    CommandLineRunner seedTestAdmin(AppUserRepository appUserRepository,
                                    LocationRepository locationRepository,
                                    PasswordEncoder passwordEncoder) {
        return args -> {
            if (appUserRepository.count() == 0) {
                Location headquarters = locationRepository.save(new Location("Hauptsitz"));

                AppUser admin = new AppUser(
                        "Test Admin",
                        "admin@example.com",
                        passwordEncoder.encode("admin123"),
                        headquarters
                );
                admin.getRoles().add(Role.ADMIN);
                appUserRepository.save(admin);
            }
        };
    }
}