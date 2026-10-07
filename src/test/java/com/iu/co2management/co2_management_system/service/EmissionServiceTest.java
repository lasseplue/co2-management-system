package com.iu.co2management.co2_management_system.service;

import com.iu.co2management.co2_management_system.entity.AppUser;
import com.iu.co2management.co2_management_system.entity.EmissionEntry;
import com.iu.co2management.co2_management_system.entity.Location;
import com.iu.co2management.co2_management_system.repository.AppUserRepository;
import com.iu.co2management.co2_management_system.repository.EmissionEntryRepository;
import com.iu.co2management.co2_management_system.repository.LocationRepository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmissionServiceTest {

    private static final String EMAIL = "user@example.com";
    private static final LocalDate FROM = LocalDate.of(2026, 9, 1);
    private static final LocalDate TO = LocalDate.of(2026, 9, 30);

    @Mock
    private EmissionEntryRepository entryRepository;
    @Mock
    private AppUserRepository userRepository;
    @Mock
    private LocationRepository locationRepository;

    private EmissionService service;
    private Location ownLocation;
    private AppUser currentUser;

    @BeforeEach
    void setUp() {
        service = new EmissionService(entryRepository, userRepository, locationRepository);

        // Location hat keinen Setter für die id, deshalb ein Mock
        ownLocation = mock(Location.class);
        lenient().when(ownLocation.getId()).thenReturn(10L);
        lenient().when(ownLocation.getName()).thenReturn("Hamburg");
        currentUser = new AppUser("Test User", EMAIL, "hash", ownLocation);
    }

    @AfterEach
    void clearLogin() {
        SecurityContextHolder.clearContext();
    }

    // ---------------------------------------------------------------- Rechte-Matrix

    @ParameterizedTest
    @CsvSource({
            "ADMIN,                  true,  true,  true",
            "EXECUTIVE,              true,  false, false",
            "SUSTAINABILITY_OFFICER, false, false, true",
            "USER,                   false, false, false"
    })
    void roleMatrix(String role, boolean readAll, boolean writeAll, boolean record) {
        loginAs(role);

        assertThat(service.canReadAllLocations()).as("canReadAllLocations").isEqualTo(readAll);
        assertThat(service.canWriteAllLocations()).as("canWriteAllLocations").isEqualTo(writeAll);
        assertThat(service.canRecordEmissions()).as("canRecordEmissions").isEqualTo(record);
    }

    @Test
    void multipleRolesCombineTheirRights() {
        // Rollen sind ein Set am Benutzer: Führungskraft + Nachhaltigkeitsbeauftragte:r
        loginAs("EXECUTIVE", "SUSTAINABILITY_OFFICER");

        assertThat(service.canReadAllLocations()).isTrue();
        assertThat(service.canRecordEmissions()).isTrue();
        assertThat(service.canWriteAllLocations()).isFalse();
    }

    // ---------------------------------------------------------------- Lesen über Standorte hinweg

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUSTAINABILITY_OFFICER"})
    void getAllLocationsIsDeniedWithoutReadAllRole(String role) {
        loginAs(role);

        assertThatThrownBy(() -> service.getAllLocations()).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(locationRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUSTAINABILITY_OFFICER"})
    void getEmissionsForLocationIsDeniedWithoutReadAllRole(String role) {
        loginAs(role);

        assertThatThrownBy(() -> service.getEmissionsForLocation(5L, FROM, TO))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(entryRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUSTAINABILITY_OFFICER"})
    void getAllLocationsSummaryIsDeniedWithoutReadAllRole(String role) {
        loginAs(role);

        assertThatThrownBy(() -> service.getAllLocationsSummary(FROM, TO))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(locationRepository, entryRepository);
    }

    @ParameterizedTest
    @ValueSource(strings = {"ADMIN", "EXECUTIVE"})
    void adminAndExecutiveMayReadAllLocations(String role) {
        loginAs(role);
        Location other = mock(Location.class);
        EmissionEntry entry = entry("Strom", 10.0);
        when(locationRepository.findAll()).thenReturn(List.of(other));
        when(entryRepository.findByLocationIdAndDateBetweenOrderByDateAsc(5L, FROM, TO))
                .thenReturn(List.of(entry));

        assertThat(service.getAllLocations()).containsExactly(other);
        assertThat(service.getEmissionsForLocation(5L, FROM, TO)).containsExactly(entry);
    }

    // ---------------------------------------------------------------- Schreiben in fremde Standorte

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUSTAINABILITY_OFFICER", "EXECUTIVE"})
    void recordEmissionForLocationIsDeniedExceptForAdmin(String role) {
        loginAs(role);

        assertThatThrownBy(() -> service.recordEmissionForLocation(5L, "Strom", 10.0, FROM))
                .isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(locationRepository);
        verify(entryRepository, never()).save(any());
    }

    @Test
    void adminRecordsForForeignLocationButRecordedByIsTheLoggedInAdmin() {
        loginAs("ADMIN");
        Location target = mock(Location.class);
        when(locationRepository.findById(5L)).thenReturn(Optional.of(target));
        when(entryRepository.save(any(EmissionEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmissionEntry saved = service.recordEmissionForLocation(5L, "Strom", 12.5, FROM);

        assertThat(saved.getLocation()).isSameAs(target);
        assertThat(saved.getRecordedBy()).isSameAs(currentUser);
        assertThat(saved.getCategory()).isEqualTo("Strom");
        assertThat(saved.getAmountKgCo2e()).isEqualTo(12.5);
        assertThat(saved.getDate()).isEqualTo(FROM);
    }

    @Test
    void recordEmissionForUnknownLocationFails() {
        loginAs("ADMIN");

        assertThatThrownBy(() -> service.recordEmissionForLocation(99L, "Strom", 10.0, FROM))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("99");
        verify(entryRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- Erfassen im eigenen Standort

    @ParameterizedTest
    @ValueSource(strings = {"SUSTAINABILITY_OFFICER", "ADMIN"})
    void officerAndAdminRecordForOwnLocationAsThemselves(String role) {
        loginAs(role);
        when(entryRepository.save(any(EmissionEntry.class))).thenAnswer(invocation -> invocation.getArgument(0));

        EmissionEntry saved = service.recordEmission("Heizung", 80.0, FROM);

        assertThat(saved.getLocation()).isSameAs(ownLocation);
        assertThat(saved.getRecordedBy()).isSameAs(currentUser);
        assertThat(saved.getCategory()).isEqualTo("Heizung");
        assertThat(saved.getAmountKgCo2e()).isEqualTo(80.0);
        assertThat(saved.getDate()).isEqualTo(FROM);
    }

    @ParameterizedTest
    @ValueSource(strings = {"USER", "EXECUTIVE"})
    void recordEmissionIsDeniedWithoutRecordRole(String role) {
        loginAs(role);

        assertThatThrownBy(() -> service.recordEmission("Strom", 10.0, FROM))
                .isInstanceOf(AccessDeniedException.class);
        verify(entryRepository, never()).save(any());
    }

    // ---------------------------------------------------------------- Standort-Scoping und Aggregation

    @ParameterizedTest
    @ValueSource(strings = {"USER", "SUSTAINABILITY_OFFICER", "EXECUTIVE", "ADMIN"})
    void ownLocationEmissionsAreAlwaysQueriedWithTheStandortOfTheLoggedInUser(String role) {
        loginAs(role);
        EmissionEntry entry = entry("Strom", 10.0);
        when(entryRepository.findByLocationIdAndDateBetweenOrderByDateAsc(10L, FROM, TO))
                .thenReturn(List.of(entry));

        List<EmissionEntry> result = service.getEmissionsForOwnLocation(FROM, TO);

        assertThat(result).containsExactly(entry);
        verify(entryRepository).findByLocationIdAndDateBetweenOrderByDateAsc(10L, FROM, TO);
        verifyNoMoreInteractions(entryRepository);   // keine Abfrage eines anderen Standorts
    }

    @Test
    void totalForOwnLocationSumsAllEntries() {
        loginAs("USER");
        when(entryRepository.findByLocationIdAndDateBetweenOrderByDateAsc(10L, FROM, TO))
                .thenReturn(List.of(entry("Strom", 10.5), entry("Heizung", 20.25)));

        assertThat(service.getTotalKgCo2eForOwnLocation(FROM, TO)).isEqualTo(30.75);
    }

    @Test
    void totalForOwnLocationIsZeroWithoutEntries() {
        loginAs("USER");
        when(entryRepository.findByLocationIdAndDateBetweenOrderByDateAsc(10L, FROM, TO))
                .thenReturn(List.of());

        assertThat(service.getTotalKgCo2eForOwnLocation(FROM, TO)).isZero();
    }

    @Test
    void summaryHasOneTotalPerLocationIncludingEmptyOnes() {
        loginAs("EXECUTIVE");
        Location sued = mock(Location.class);
        Location nord = mock(Location.class);
        when(sued.getId()).thenReturn(1L);
        when(sued.getName()).thenReturn("Süd");
        when(nord.getId()).thenReturn(2L);
        when(nord.getName()).thenReturn("Nord");
        when(locationRepository.findAll()).thenReturn(List.of(sued, nord));
        when(entryRepository.findByLocationIdAndDateBetweenOrderByDateAsc(1L, FROM, TO))
                .thenReturn(List.of(entry("Strom", 10.0), entry("Heizung", 20.0)));
        when(entryRepository.findByLocationIdAndDateBetweenOrderByDateAsc(2L, FROM, TO))
                .thenReturn(List.of());

        assertThat(service.getAllLocationsSummary(FROM, TO)).containsExactly(
                new LocationSummary("Süd", 30.0),
                new LocationSummary("Nord", 0.0));
    }

    // ---------------------------------------------------------------- Benutzer und Randfälle

    @Test
    void currentUserLocationNameComesFromTheLoggedInUser() {
        loginAs("USER");

        assertThat(service.getCurrentUserLocationName()).isEqualTo("Hamburg");
    }

    @Test
    void loggedInUserMissingInDatabaseFails() {
        // Eingeloggt, aber ohne passenden Datensatz (z. B. Benutzer wurde gelöscht, Sitzung läuft noch)
        setAuthentication("ghost@example.com", "USER");

        assertThatThrownBy(() -> service.getEmissionsForOwnLocation(FROM, TO))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("ghost@example.com");
    }

    // ---------------------------------------------------------------- Hilfsmethoden

    /** Simuliert einen eingeloggten Benutzer mit den Rollen und lässt ihn in der Datenbank "existieren". */
    private void loginAs(String... roles) {
        setAuthentication(EMAIL, roles);
        lenient().when(userRepository.findByEmailWithLocation(EMAIL)).thenReturn(Optional.of(currentUser));
    }

    private static void setAuthentication(String email, String... roles) {
        List<SimpleGrantedAuthority> authorities = Arrays.stream(roles)
                .map(role -> new SimpleGrantedAuthority("ROLE_" + role))
                .toList();
        SecurityContextHolder.getContext()
                .setAuthentication(new UsernamePasswordAuthenticationToken(EMAIL.equals(email) ? email : email, "n/a", authorities));
    }

    private static EmissionEntry entry(String category, double amount) {
        return new EmissionEntry(category, amount, FROM, null, null);
    }
}