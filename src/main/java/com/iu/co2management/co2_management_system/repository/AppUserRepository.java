package com.iu.co2management.co2_management_system.repository;

import com.iu.co2management.co2_management_system.entity.AppUser;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import java.util.List;
import java.util.Optional;

public interface AppUserRepository extends JpaRepository<AppUser, Long> {

    Optional<AppUser> findByEmail(String email);

    @Query("SELECT u FROM AppUser u JOIN FETCH u.location")
    List<AppUser> findAllWithLocation();
}