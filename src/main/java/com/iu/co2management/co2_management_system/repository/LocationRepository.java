package com.iu.co2management.co2_management_system.repository;

import com.iu.co2management.co2_management_system.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.Optional;

public interface LocationRepository extends JpaRepository<Location, Long> {
    Optional<Location> findByName(String name);
}