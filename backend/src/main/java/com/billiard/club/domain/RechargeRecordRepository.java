package com.billiard.club.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface RechargeRecordRepository extends JpaRepository<RechargeRecord, Long> {

    List<RechargeRecord> findAllByOrderByIdDesc();

    List<RechargeRecord> findByMemberIdOrderByIdDesc(Long memberId);
}
