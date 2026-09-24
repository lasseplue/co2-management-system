package com.iu.co2management.co2_management_system.repository;

import com.iu.co2management.co2_management_system.entity.EmissionEntry;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;

public interface EmissionEntryRepository extends JpaRepository<EmissionEntry, Long> {

    List<EmissionEntry> findByLocationId(Long locationId);

    List<EmissionEntry> findByLocationIdAndDateBetween(Long locationId, LocalDate from, LocalDate to);
}