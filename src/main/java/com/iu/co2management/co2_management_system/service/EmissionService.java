package com.iu.co2management.co2_management_system.service;

import com.iu.co2management.co2_management_system.entity.AppUser;
import com.iu.co2management.co2_management_system.entity.EmissionEntry;
import com.iu.co2management.co2_management_system.repository.AppUserRepository;
import com.iu.co2management.co2_management_system.repository.EmissionEntryRepository;
import com.iu.co2management.co2_management_system.repository.LocationRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import com.iu.co2management.co2_management_system.entity.Location;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.GrantedAuthority;
import java.util.Set;
import java.util.stream.Collectors;

import java.time.LocalDate;
import java.util.List;

@Service
public class EmissionService {

    public static final List<String> CATEGORIES = List.of("Strom", "Heizung", "Mobilität", "Sonstiges");
    private static final Set<String> READ_ALL_ROLES = Set.of("ADMIN", "EXECUTIVE");
    private static final Set<String> WRITE_ALL_ROLES = Set.of("ADMIN");
    private static final Set<String> RECORD_ROLES = Set.of("SUSTAINABILITY_OFFICER", "ADMIN");
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
    public boolean canReadAllLocations() {
        return hasAnyRole(READ_ALL_ROLES);
    }

    public boolean canWriteAllLocations() {
        return hasAnyRole(WRITE_ALL_ROLES);
    }

    public boolean canRecordEmissions() {
        return hasAnyRole(RECORD_ROLES);
    }

    public List<Location> getAllLocations() {
        requireAnyRole(READ_ALL_ROLES);
        return locationRepository.findAll();
    }

    public List<EmissionEntry> getEmissionsForLocation(Long locationId, LocalDate from, LocalDate to) {
        requireAnyRole(READ_ALL_ROLES);
        return emissionEntryRepository.findByLocationIdAndDateBetweenOrderByDateAsc(locationId, from, to);
    }

    public EmissionEntry recordEmissionForLocation(Long locationId, String category, double amountKgCo2e, LocalDate date) {
        requireAnyRole(WRITE_ALL_ROLES);
        Location location = locationRepository.findById(locationId)
                .orElseThrow(() -> new IllegalArgumentException("Standort nicht gefunden: " + locationId));
        EmissionEntry entry = new EmissionEntry(category, amountKgCo2e, date, location, getCurrentUser());
        return emissionEntryRepository.save(entry);
    }

    private boolean hasAnyRole(Set<String> roles) {
        Set<String> authorities = SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
                .map(GrantedAuthority::getAuthority)
                .collect(Collectors.toSet());
        return roles.stream().anyMatch(role -> authorities.contains("ROLE_" + role));
    }

    private void requireAnyRole(Set<String> roles) {
        if (!hasAnyRole(roles)) {
            throw new AccessDeniedException("Keine Berechtigung für standortübergreifenden Zugriff");
        }
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