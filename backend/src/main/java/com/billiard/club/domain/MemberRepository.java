package com.billiard.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface MemberRepository extends JpaRepository<Member, Long> {

    List<Member> findAllByOrderByCardNoAsc();

    Optional<Member> findByCardNo(String cardNo);

    boolean existsByCardNo(String cardNo);
}
