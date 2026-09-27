package com.billiard.club.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TournamentRevisionMatchRepository extends JpaRepository<TournamentRevisionMatch, Long> {

    List<TournamentRevisionMatch> findByRevisionIdOrderByIdAsc(Long revisionId);
}
