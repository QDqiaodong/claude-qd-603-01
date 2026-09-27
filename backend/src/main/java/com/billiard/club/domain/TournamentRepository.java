package com.billiard.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentRepository extends JpaRepository<Tournament, Long> {

    List<Tournament> findAllByOrderByIdDesc();

    Optional<Tournament> findByTournamentNo(String tournamentNo);

    boolean existsByTournamentNo(String tournamentNo);
}
