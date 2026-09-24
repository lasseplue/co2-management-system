package com.iu.co2management.co2_management_system.repository;

import com.iu.co2management.co2_management_system.entity.Location;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LocationRepository extends JpaRepository<Location, Long> {
}