package com.iu.co2management.co2_management_system.repository;

import com.iu.co2management.co2_management_system.entity.AppUser;
import com.iu.co2management.co2_management_system.entity.EmissionEntry;
import com.iu.co2management.co2_management_system.entity.Location;
import com.iu.co2management.co2_management_system.entity.Role;

import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.dao.DataAccessException;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
class RepositoryIntegrationTest {

    private static final LocalDate DAY = LocalDate.of(2026, 9, 1);

    @Autowired
    private TestEntityManager em;
    @Autowired
    private LocationRepository locationRepository;
    @Autowired
    private AppUserRepository userRepository;
    @Autowired
    private EmissionEntryRepository entryRepository;

    // ---------------------------------------------------------------- Löschverhalten

    @Test
    void deletingALocationDeletesItsEmissionEntriesButNotThoseOfOtherLocations() {
        Location hamburg = em.persist(new Location("Hamburg"));
        Location berlin = em.persist(new Location("Berlin"));
        AppUser admin = em.persist(new AppUser("Admin", "admin@example.com", "hash", berlin));
        em.persist(new EmissionEntry("Strom", 10.0, DAY, hamburg, admin));
        em.persist(new EmissionEntry("Heizung", 20.0, DAY, hamburg, null));
        em.persist(new EmissionEntry("Strom", 30.0, DAY, berlin, admin));
        Long hamburgId = hamburg.getId();
        Long berlinId = berlin.getId();
        flushAndClear();

        locationRepository.deleteById(hamburgId);
        flushAndClear();

        assertThat(entryRepository.findByLocationId(hamburgId)).isEmpty();
        assertThat(entryRepository.findByLocationId(berlinId)).hasSize(1);
        assertThat(entryRepository.count()).isEqualTo(1);
    }

    @Test
    void deletingAUserKeepsTheEntriesAndClearsRecordedBy() {
        Location hamburg = em.persist(new Location("Hamburg"));
        AppUser officer = new AppUser("Officer", "officer@example.com", "hash", hamburg);
        officer.getRoles().add(Role.SUSTAINABILITY_OFFICER);
        officer.getRoles().add(Role.USER);
        em.persist(officer);
        em.persist(new EmissionEntry("Strom", 10.0, DAY, hamburg, officer));
        em.persist(new EmissionEntry("Heizung", 20.0, DAY, hamburg, officer));
        Long hamburgId = hamburg.getId();
        Long officerId = officer.getId();
        flushAndClear();

        userRepository.deleteById(officerId);
        flushAndClear();

        List<EmissionEntry> entries = entryRepository.findByLocationId(hamburgId);
        assertThat(entries).hasSize(2);
        assertThat(entries).allSatisfy(entry -> assertThat(entry.getRecordedBy()).isNull());
        assertThat(userRepository.findById(officerId)).isEmpty();
        assertThat(countRows("user_role")).as("Rollen des gelöschten Benutzers").isZero();
    }

    @Test
    void aLocationWithUsersCannotBeDeleted() {
        Location hamburg = em.persist(new Location("Hamburg"));
        em.persist(new AppUser("User", "user@example.com", "hash", hamburg));
        Long hamburgId = hamburg.getId();
        flushAndClear();

        // Diese Abfrage nutzt die LocationManagementView für die Vorabprüfung
        assertThat(userRepository.existsByLocationId(hamburgId)).isTrue();

        // Und die Datenbank würde das Löschen selbst dann verweigern, wenn die Prüfung fehlen würde
        assertThatThrownBy(() -> {
            locationRepository.deleteById(hamburgId);
            locationRepository.flush();
        }).isInstanceOf(DataAccessException.class);
    }

    // ---------------------------------------------------------------- Eindeutigkeit

    @Test
    void locationNamesMustBeUnique() {
        locationRepository.saveAndFlush(new Location("Hamburg"));

        assertThatThrownBy(() -> locationRepository.saveAndFlush(new Location("Hamburg")))
                .isInstanceOf(DataAccessException.class);
    }

    @Test
    void userEmailsMustBeUnique() {
        Location hamburg = em.persist(new Location("Hamburg"));
        userRepository.saveAndFlush(new AppUser("Erste", "same@example.com", "hash", hamburg));

        assertThatThrownBy(() -> userRepository.saveAndFlush(
                new AppUser("Zweite", "same@example.com", "hash", hamburg)))
                .isInstanceOf(DataAccessException.class);
    }

    // ---------------------------------------------------------------- Abfragen

    @Test
    void dateRangeQueryIsInclusiveSortedAndLimitedToTheLocation() {
        Location hamburg = em.persist(new Location("Hamburg"));
        Location berlin = em.persist(new Location("Berlin"));
        LocalDate from = LocalDate.of(2026, 9, 1);
        LocalDate to = LocalDate.of(2026, 9, 30);
        // absichtlich unsortiert eingefügt
        em.persist(new EmissionEntry("Strom", 1.0, LocalDate.of(2026, 8, 31), hamburg, null));   // vor dem Zeitraum
        em.persist(new EmissionEntry("Strom", 2.0, to, hamburg, null));                          // Randtag Ende
        em.persist(new EmissionEntry("Strom", 3.0, LocalDate.of(2026, 9, 15), hamburg, null));
        em.persist(new EmissionEntry("Strom", 4.0, from, hamburg, null));                        // Randtag Anfang
        em.persist(new EmissionEntry("Strom", 5.0, LocalDate.of(2026, 10, 1), hamburg, null));   // nach dem Zeitraum
        em.persist(new EmissionEntry("Strom", 6.0, LocalDate.of(2026, 9, 10), berlin, null));    // anderer Standort
        Long hamburgId = hamburg.getId();
        flushAndClear();

        List<EmissionEntry> result = entryRepository
                .findByLocationIdAndDateBetweenOrderByDateAsc(hamburgId, from, to);

        assertThat(result).extracting(EmissionEntry::getDate)
                .containsExactly(from, LocalDate.of(2026, 9, 15), to);
    }

    @Test
    void findByEmailKeepsTheLocationLazyButFindByEmailWithLocationLoadsIt() {
        // Das ist die Ursache der LazyInitializationException aus dem Projekt, als Test festgehalten
        Location hamburg = em.persist(new Location("Hamburg"));
        AppUser user = new AppUser("User", "user@example.com", "hash", hamburg);
        user.getRoles().add(Role.USER);
        em.persist(user);
        flushAndClear();

        AppUser lazy = userRepository.findByEmail("user@example.com").orElseThrow();
        assertThat(Hibernate.isInitialized(lazy.getLocation())).isFalse();

        em.clear();

        AppUser loaded = userRepository.findByEmailWithLocation("user@example.com").orElseThrow();
        assertThat(Hibernate.isInitialized(loaded.getLocation())).isTrue();
        assertThat(loaded.getLocation().getName()).isEqualTo("Hamburg");
        assertThat(loaded.getRoles()).containsExactly(Role.USER);
    }

    // ---------------------------------------------------------------- Hilfsmethoden

    /** Schreibt alles in die Datenbank und leert den Speicher, damit spätere Abfragen wirklich die DB fragen. */
    private void flushAndClear() {
        em.flush();
        em.clear();
    }

    private long countRows(String table) {
        Object count = em.getEntityManager()
                .createNativeQuery("select count(*) from " + table)
                .getSingleResult();
        return ((Number) count).longValue();
    }
}