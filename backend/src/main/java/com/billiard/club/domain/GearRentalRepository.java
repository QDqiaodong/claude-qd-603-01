package com.billiard.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GearRentalRepository extends JpaRepository<GearRental, Long> {

    List<GearRental> findAllByOrderByIdDesc();

    /** 某器材当前租借中的单（借出未还禁止再借） */
    Optional<GearRental> findFirstByGearIdAndStatus(Long gearId, String status);

    boolean existsByRentalNo(String rentalNo);
}
