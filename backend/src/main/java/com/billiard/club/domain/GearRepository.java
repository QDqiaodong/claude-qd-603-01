package com.billiard.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GearRepository extends JpaRepository<Gear, Long> {

    List<Gear> findAllByOrderByGearNoAsc();

    Optional<Gear> findByGearNo(String gearNo);

    boolean existsByGearNo(String gearNo);
}
