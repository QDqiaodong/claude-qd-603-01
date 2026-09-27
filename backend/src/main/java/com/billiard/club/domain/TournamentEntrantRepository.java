package com.billiard.club.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentEntrantRepository extends JpaRepository<TournamentEntrant, Long> {

    List<TournamentEntrant> findByTournamentIdOrderBySeedNoAsc(Long tournamentId);

    long countByTournamentIdAndMemberId(Long tournamentId, Long memberId);
}
