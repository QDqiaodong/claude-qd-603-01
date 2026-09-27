package com.billiard.club.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentRevisionRepository extends JpaRepository<TournamentRevision, Long> {

    List<TournamentRevision> findByTournamentIdOrderByIdDesc(Long tournamentId);
}
