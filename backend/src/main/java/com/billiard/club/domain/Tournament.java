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
 * 括号在创建时一次性生成（胜者组 / 败者组 / 总决赛），结果只允许被
 * 确认、弃权推进或管理员受控回退驱动；赛事模块与会员、散客、球台计费
 * 完全隔离，不读写任何旧表的余额、状态。
 */
@Entity
@Table(name = "tournament")
public class Tournament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_name", nullable = false, length = 128)
    private String tournamentName;

    @Column(name = "entrant_count", nullable = false)
    private Integer entrantCount;

    @Column(name = "bracket_size", nullable = false)
    private Integer bracketSize;

    @Column(name = "bye_count", nullable = false)
    private Integer byeCount;

    /** LIVE 进行中 / FINISHED 已完赛 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "champion_id")
    private Long championId;

    @Column(name = "current_version", nullable = false)
    private Integer currentVersion;

    @Column(name = "created_by", nullable = false, length = 64)
    private String createdBy;

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

    public String getTournamentName() {
        return tournamentName;
    }

    public void setTournamentName(String tournamentName) {
        this.tournamentName = tournamentName;
    }

    public Integer getEntrantCount() {
        return entrantCount;
    }

    public void setEntrantCount(Integer entrantCount) {
        this.entrantCount = entrantCount;
    }

    public Integer getBracketSize() {
        return bracketSize;
    }

    public void setBracketSize(Integer bracketSize) {
        this.bracketSize = bracketSize;
    }

    public Integer getByeCount() {
        return byeCount;
    }

    public void setByeCount(Integer byeCount) {
        this.byeCount = byeCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getChampionId() {
        return championId;
    }

    public void setChampionId(Long championId) {
        this.championId = championId;
    }

    public Integer getCurrentVersion() {
        return currentVersion;
    }

    public void setCurrentVersion(Integer currentVersion) {
        this.currentVersion = currentVersion;
    }

    public String getCreatedBy() {
        return createdBy;
    }

    public void setCreatedBy(String createdBy) {
        this.createdBy = createdBy;
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
