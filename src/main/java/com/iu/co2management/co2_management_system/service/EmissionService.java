package com.iu.co2management.co2_management_system.service;

import com.iu.co2management.co2_management_system.entity.AppUser;
import com.iu.co2management.co2_management_system.entity.EmissionEntry;
import com.iu.co2management.co2_management_system.repository.AppUserRepository;
import com.iu.co2management.co2_management_system.repository.EmissionEntryRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class EmissionService {

    private final EmissionEntryRepository emissionEntryRepository;
    private final AppUserRepository appUserRepository;

    public EmissionService(EmissionEntryRepository emissionEntryRepository,
                           AppUserRepository appUserRepository) {
        this.emissionEntryRepository = emissionEntryRepository;
        this.appUserRepository = appUserRepository;
    }

    public EmissionEntry recordEmission(String category, double amountKgCo2e, LocalDate date) {
        AppUser currentUser = getCurrentUser();
        EmissionEntry entry = new EmissionEntry(category, amountKgCo2e, date, currentUser.getLocation(), currentUser);
        return emissionEntryRepository.save(entry);
    }

    public List<EmissionEntry> getEmissionsForOwnLocation(LocalDate from, LocalDate to) {
        AppUser currentUser = getCurrentUser();
        return emissionEntryRepository.findByLocationIdAndDateBetween(currentUser.getLocation().getId(), from, to);
    }

    public double getTotalKgCo2eForOwnLocation(LocalDate from, LocalDate to) {
        return getEmissionsForOwnLocation(from, to).stream()
                .mapToDouble(EmissionEntry::getAmountKgCo2e)
                .sum();
    }

    private AppUser getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return appUserRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Eingeloggter Benutzer nicht in der Datenbank gefunden: " + email));
    }
}