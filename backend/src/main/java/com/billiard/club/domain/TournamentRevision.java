package com.billiard.club.domain;

import java.time.LocalDateTime;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 赛事纠错流水：只增不改。
 * 每次赛果确认（RESULT）、退赛不战而胜（WALKOVER）、管理员回退（ROLLBACK）、
 * 回退点更正（CORRECT）都留一条；版本号、操作人、原因、受影响场次与前后
 * 快照（detailJson）全部可追溯，页面刷新后括号/列表/冠军状态以最新版本为准。
 */
@Entity
@Table(name = "tournament_revision")
public class TournamentRevision {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Column(name = "version_no", nullable = false)
    private Integer versionNo;

    /** RESULT / WALKOVER / ROLLBACK / CORRECT */
    @Column(name = "change_type", nullable = false, length = 16)
    private String changeType;

    @Column(name = "operator_name", nullable = false, length = 64)
    private String operatorName;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "anchor_match_id")
    private Long anchorMatchId;

    @Column(name = "affected_match_ids", length = 512)
    private String affectedMatchIds;

    @Column(name = "detail_json", columnDefinition = "LONGTEXT")
    private String detailJson;

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

    public Integer getVersionNo() {
        return versionNo;
    }

    public void setVersionNo(Integer versionNo) {
        this.versionNo = versionNo;
    }

    public String getChangeType() {
        return changeType;
    }

    public void setChangeType(String changeType) {
        this.changeType = changeType;
    }

    public String getOperatorName() {
        return operatorName;
    }

    public void setOperatorName(String operatorName) {
        this.operatorName = operatorName;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public Long getAnchorMatchId() {
        return anchorMatchId;
    }

    public void setAnchorMatchId(Long anchorMatchId) {
        this.anchorMatchId = anchorMatchId;
    }

    public String getAffectedMatchIds() {
        return affectedMatchIds;
    }

    public void setAffectedMatchIds(String affectedMatchIds) {
        this.affectedMatchIds = affectedMatchIds;
    }

    public String getDetailJson() {
        return detailJson;
    }

    public void setDetailJson(String detailJson) {
        this.detailJson = detailJson;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
