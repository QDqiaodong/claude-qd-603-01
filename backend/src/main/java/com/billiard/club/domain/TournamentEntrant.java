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
 * 赛事参赛者：会员（member_id 弱引用 club_member）或临时散客（member_id 为 NULL）。
 *
 * 种子号在创建时排定，高种子自动占轮空位。
 * 失利次数不落库：所有失利都已经物化为场次结果，读括号时从结果图实时汇总，
 * 这样回退/纠错清空结果后参赛者状态天然跟着回退，不需要额外修补。
 */
@Entity
@Table(name = "tournament_entrant")
public class TournamentEntrant {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    /** 会员参赛者的弱引用；临时参赛者为 NULL */
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "display_name", nullable = false, length = 64)
    private String displayName;

    /** MEMBER 会员 / WALK_IN 临时参赛者 */
    @Column(name = "entrant_kind", nullable = false, length = 16)
    private String entrantKind;

    @Column(name = "seed_no", nullable = false)
    private Integer seedNo;

    @Column(name = "withdrawn", nullable = false)
    private Boolean withdrawn = false;

    @Column(name = "withdraw_reason", length = 255)
    private String withdrawReason;

    @Column(name = "withdrew_at")
    private LocalDateTime withdrewAt;

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

    public String getDisplayName() {
        return displayName;
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }

    public String getEntrantKind() {
        return entrantKind;
    }

    public void setEntrantKind(String entrantKind) {
        this.entrantKind = entrantKind;
    }

    public Integer getSeedNo() {
        return seedNo;
    }

    public void setSeedNo(Integer seedNo) {
        this.seedNo = seedNo;
    }

    public Boolean getWithdrawn() {
        return withdrawn;
    }

    public void setWithdrawn(Boolean withdrawn) {
        this.withdrawn = withdrawn;
    }

    public String getWithdrawReason() {
        return withdrawReason;
    }

    public void setWithdrawReason(String withdrawReason) {
        this.withdrawReason = withdrawReason;
    }

    public LocalDateTime getWithdrewAt() {
        return withdrewAt;
    }

    public void setWithdrewAt(LocalDateTime withdrewAt) {
        this.withdrewAt = withdrewAt;
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
