package com.iu.co2management.co2_management_system.config;

import com.iu.co2management.co2_management_system.entity.AppUser;
import com.iu.co2management.co2_management_system.entity.EmissionEntry;
import com.iu.co2management.co2_management_system.entity.Location;
import com.iu.co2management.co2_management_system.entity.Role;
import com.iu.co2management.co2_management_system.repository.AppUserRepository;
import com.iu.co2management.co2_management_system.repository.EmissionEntryRepository;
import com.iu.co2management.co2_management_system.repository.LocationRepository;
import com.iu.co2management.co2_management_system.service.EmissionService;
import org.springframework.boot.CommandLineRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDate;
import java.util.List;

@Configuration
public class DevDataInitializer {

    @Bean
    CommandLineRunner seedDevData(AppUserRepository appUserRepository,
                                  LocationRepository locationRepository,
                                  EmissionEntryRepository emissionEntryRepository,
                                  PasswordEncoder passwordEncoder) {
        return args -> {
            if (appUserRepository.count() > 0) {
                return;
            }

            Location hauptsitz = locationRepository.save(new Location("Kassel"));
            Location werkNord = locationRepository.save(new Location("Berlin"));
            locationRepository.save(new Location("Zürich"));

            createUser(appUserRepository, passwordEncoder, "Test Admin", "admin@example.com", "admin123", hauptsitz, Role.ADMIN);
            createUser(appUserRepository, passwordEncoder, "Test Führungskraft", "executive@example.com", "test123", hauptsitz, Role.EXECUTIVE);
            createUser(appUserRepository, passwordEncoder, "Test Benutzer", "user@example.com", "test123", hauptsitz, Role.USER);
            AppUser officerHq = createUser(appUserRepository, passwordEncoder, "Nachhaltigkeit Hauptsitz", "officer@example.com", "test123", hauptsitz, Role.SUSTAINABILITY_OFFICER);
            AppUser officerNord = createUser(appUserRepository, passwordEncoder, "Nachhaltigkeit Nord", "officer.nord@example.com", "test123", werkNord, Role.SUSTAINABILITY_OFFICER);

            seedEmissions(emissionEntryRepository, hauptsitz, officerHq, 1.0);
            seedEmissions(emissionEntryRepository, werkNord, officerNord, 1.6);
        };
    }

    private AppUser createUser(AppUserRepository repository, PasswordEncoder encoder,
                               String name, String email, String password, Location location, Role role) {
        AppUser user = new AppUser(name, email, encoder.encode(password), location);
        user.getRoles().add(role);
        return repository.save(user);
    }

    private void seedEmissions(EmissionEntryRepository repository, Location location, AppUser recordedBy, double factor) {
        List<String> categories = EmissionService.CATEGORIES;
        for (int i = 0; i < 30; i++) {
            if (i % 4 == 3) {
                continue;
            }
            String category = categories.get(i % categories.size());
            double amount = Math.round((40 + (i * 7) % 90) * factor * 10) / 10.0;
            repository.save(new EmissionEntry(category, amount, LocalDate.now().minusDays(i), location, recordedBy));
        }
    }
}