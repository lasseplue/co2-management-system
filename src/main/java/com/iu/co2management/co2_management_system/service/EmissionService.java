package com.iu.co2management.co2_management_system.service;

import com.iu.co2management.co2_management_system.entity.AppUser;
import com.iu.co2management.co2_management_system.entity.EmissionEntry;
import com.iu.co2management.co2_management_system.repository.AppUserRepository;
import com.iu.co2management.co2_management_system.repository.EmissionEntryRepository;
import com.iu.co2management.co2_management_system.repository.LocationRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.util.List;

@Service
public class EmissionService {

    public static final List<String> CATEGORIES = List.of("Strom", "Heizung", "Mobilität", "Sonstiges");
    private final EmissionEntryRepository emissionEntryRepository;
    private final AppUserRepository appUserRepository;
    private final LocationRepository locationRepository;

    public EmissionService(EmissionEntryRepository emissionEntryRepository,
                           AppUserRepository appUserRepository,
                           LocationRepository locationRepository) {
        this.emissionEntryRepository = emissionEntryRepository;
        this.appUserRepository = appUserRepository;
        this.locationRepository = locationRepository;
    }

    public EmissionEntry recordEmission(String category, double amountKgCo2e, LocalDate date) {
        AppUser currentUser = getCurrentUser();
        EmissionEntry entry = new EmissionEntry(category, amountKgCo2e, date, currentUser.getLocation(), currentUser);
        return emissionEntryRepository.save(entry);
    }

    public List<EmissionEntry> getEmissionsForOwnLocation(LocalDate from, LocalDate to) {
        AppUser currentUser = getCurrentUser();
        return emissionEntryRepository.findByLocationIdAndDateBetweenOrderByDateAsc(currentUser.getLocation().getId(), from, to);
    }

    public double getTotalKgCo2eForOwnLocation(LocalDate from, LocalDate to) {
        return getEmissionsForOwnLocation(from, to).stream()
                .mapToDouble(EmissionEntry::getAmountKgCo2e)
                .sum();
    }

    public List<LocationSummary> getAllLocationsSummary(LocalDate from, LocalDate to) {
        return locationRepository.findAll().stream()
                .map(location -> {
                    double total = emissionEntryRepository
                            .findByLocationIdAndDateBetweenOrderByDateAsc(location.getId(), from, to)
                            .stream()
                            .mapToDouble(EmissionEntry::getAmountKgCo2e)
                            .sum();
                    return new LocationSummary(location.getName(), total);
                })
                .toList();
    }

    private AppUser getCurrentUser() {
        String email = SecurityContextHolder.getContext().getAuthentication().getName();
        return appUserRepository.findByEmailWithLocation(email)
                .orElseThrow(() -> new IllegalStateException(
                        "Eingeloggter Benutzer nicht in der Datenbank gefunden: " + email));
    }

    public String getCurrentUserLocationName() {
        return getCurrentUser().getLocation().getName();
    }
}