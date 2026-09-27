package com.billiard.club.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** 赛事纠错版本（一次受控回退 = 一条版本）。 */
@Entity
@Table(name = "tournament_revision")
public class TournamentRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Column(name = "root_match_id")
    private Long rootMatchId;

    @Column(name = "operator", nullable = false, length = 64)
    private String operator;

    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    @Column(name = "corrected_winner_entry_id")
    private Long correctedWinnerEntryId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @PrePersist
    void onCreate() {
        this.createdAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Long getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(Long tournamentId) {
        this.tournamentId = tournamentId;
    }

    public Long getRootMatchId() {
        return rootMatchId;
    }

    public void setRootMatchId(Long rootMatchId) {
        this.rootMatchId = rootMatchId;
    }

    public String getOperator() {
        return operator;
    }

    public void setOperator(String operator) {
        this.operator = operator;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getCorrectedWinnerEntryId() {
        return correctedWinnerEntryId;
    }

    public void setCorrectedWinnerEntryId(Long correctedWinnerEntryId) {
        this.correctedWinnerEntryId = correctedWinnerEntryId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
