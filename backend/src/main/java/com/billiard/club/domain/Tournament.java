package com.billiard.club.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 双败淘汰赛事。
 *
 * status: DRAFT 未开赛 / RUNNING 进行中 / FINISHED 已结束。
 * bracketSize 为开赛人数向上取整到的 2 的幂；playerCount 不是 2 的幂时，
 * 差额（bracketSize - playerCount）即首轮轮空数，轮空不落比赛行。
 */
@Entity
@Table(name = "tournament")
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_no", nullable = false, unique = true, length = 32)
    private String tournamentNo;

    @Column(name = "name", nullable = false, length = 128)
    private String name;

    /** DRAFT / RUNNING / FINISHED */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "player_count", nullable = false)
    private Integer playerCount;

    @Column(name = "bracket_size")
    private Integer bracketSize;

    @Column(name = "champion_entry_id")
    private Long championEntryId;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    @PrePersist
    void onCreate() {
        LocalDateTime now = LocalDateTime.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        this.updatedAt = LocalDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public String getTournamentNo() {
        return tournamentNo;
    }

    public void setTournamentNo(String tournamentNo) {
        this.tournamentNo = tournamentNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Integer getPlayerCount() {
        return playerCount;
    }

    public void setPlayerCount(Integer playerCount) {
        this.playerCount = playerCount;
    }

    public Integer getBracketSize() {
        return bracketSize;
    }

    public void setBracketSize(Integer bracketSize) {
        this.bracketSize = bracketSize;
    }

    public Long getChampionEntryId() {
        return championEntryId;
    }

    public void setChampionEntryId(Long championEntryId) {
        this.championEntryId = championEntryId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}
