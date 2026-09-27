package com.billiard.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentMatchRepository extends JpaRepository<TournamentMatch, Long> {

    List<TournamentMatch> findByTournamentId(Long tournamentId);

    Optional<TournamentMatch> findByTournamentIdAndMatchCode(Long tournamentId, String matchCode);
}
