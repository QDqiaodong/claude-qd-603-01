package com.billiard.club.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentEntryRepository extends JpaRepository<TournamentEntry, Long> {

    List<TournamentEntry> findByTournamentIdOrderBySeedNoAsc(Long tournamentId);
}
