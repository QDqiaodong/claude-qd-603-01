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
 * 赛事参赛者。会员取 member_id；临时参赛者（散客）member_id 为 NULL。
 *
 * lossCount: 0 在胜者组 / 1 一败在败者组 / 2 两败出局。
 * status: ACTIVE 在赛 / ELIMINATED 两败出局 / WITHDRAWN 中途退赛。
 * 退赛不折算失利次数：未出场退赛 lossCount 仍为 0，一败后退赛为 1，三种情况可区分。
 */
@Entity
@Table(name = "tournament_entry")
public class TournamentEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Column(name = "member_id")
    private Long memberId;

    /** MEMBER / GUEST */
    @Column(name = "entry_type", nullable = false, length = 16)
    private String entryType;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    @Column(name = "seed_no", nullable = false)
    private Integer seedNo;

    @Column(name = "loss_count", nullable = false)
    private Integer lossCount;

    /** ACTIVE / ELIMINATED / WITHDRAWN */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "withdraw_note")
    private String withdrawNote;

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

    public Long getTournamentId() {
        return tournamentId;
    }

    public void setTournamentId(Long tournamentId) {
        this.tournamentId = tournamentId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public String getEntryType() {
        return entryType;
    }

    public void setEntryType(String entryType) {
        this.entryType = entryType;
    }

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public Integer getSeedNo() {
        return seedNo;
    }

    public void setSeedNo(Integer seedNo) {
        this.seedNo = seedNo;
    }

    public Integer getLossCount() {
        return lossCount;
    }

    public void setLossCount(Integer lossCount) {
        this.lossCount = lossCount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getWithdrawNote() {
        return withdrawNote;
    }

    public void setWithdrawNote(String withdrawNote) {
        this.withdrawNote = withdrawNote;
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
