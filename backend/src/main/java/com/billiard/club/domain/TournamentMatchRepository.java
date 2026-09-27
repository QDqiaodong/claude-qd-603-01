package com.billiard.club.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentMatchRepository extends JpaRepository<TournamentMatch, Long> {

    List<TournamentMatch> findByTournamentIdOrderByIdAsc(Long tournamentId);

    List<TournamentMatch> findByTournamentIdAndStatusOrderByIdAsc(Long tournamentId, String status);
}
