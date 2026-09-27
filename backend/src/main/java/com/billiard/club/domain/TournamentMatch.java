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
 * 赛事场次（括号图上的一个节点）。
 *
 * 括号图两条有向边：
 *   winnerNext 本场胜者去向；loserNext 本场第一次失利者去向（败者组入位）。
 *   总决赛（GRAND_FINAL）败者组冠军获胜时，winnerNext / loserNext 都指向重置赛；
 *   重置赛（GRAND_RESET）由赛果确认时动态生成，无后续边。
 *
 * 轮空也占节点：seedWinnersRound == 1 的 BYE 行与败者组的结构性轮空行都真实落库，
 * 但 resultType = BYE 表示它不是真实比赛，列表与括号视图明确标注「轮空·非比赛」。
 *
 * status: PENDING 等入位 / READY 双方到齐待开赛 / FINISHED 已结束 / VOID 双方退赛作废。
 * resultType: NORMAL 正常确认 / WALKOVER 退赛直接晋级 / BYE 轮空（非真实比赛）。
 */
@Entity
@Table(name = "tournament_match")
public class TournamentMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Column(name = "match_no", nullable = false, length = 16)
    private String matchNo;

    /** WINNERS / LOSERS / GRAND_FINAL / GRAND_RESET */
    @Column(name = "stage", nullable = false, length = 16)
    private String stage;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    @Column(name = "seq", nullable = false)
    private Integer seq;

    @Column(name = "side_a_entry_id")
    private Long sideAEntryId;

    @Column(name = "side_b_entry_id")
    private Long sideBEntryId;

    @Column(name = "side_a_source", length = 128)
    private String sideASource;

    @Column(name = "side_b_source", length = 128)
    private String sideBSource;

    @Column(name = "winner_next_match_id")
    private Long winnerNextMatchId;

    /** A / B */
    @Column(name = "winner_next_slot", length = 1)
    private String winnerNextSlot;

    @Column(name = "loser_next_match_id")
    private Long loserNextMatchId;

    /** A / B */
    @Column(name = "loser_next_slot", length = 1)
    private String loserNextSlot;

    /** PENDING / READY / FINISHED / VOID */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "winner_entry_id")
    private Long winnerEntryId;

    @Column(name = "loser_entry_id")
    private Long loserEntryId;

    /** NORMAL / WALKOVER / BYE */
    @Column(name = "result_type", length = 16)
    private String resultType;

    @Column(name = "result_note")
    private String resultNote;

    @Column(name = "confirmed_by", length = 64)
    private String confirmedBy;

    @Column(name = "confirmed_at")
    private LocalDateTime confirmedAt;

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

    public String getMatchNo() {
        return matchNo;
    }

    public void setMatchNo(String matchNo) {
        this.matchNo = matchNo;
    }

    public String getStage() {
        return stage;
    }

    public void setStage(String stage) {
        this.stage = stage;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public Long getSideAEntryId() {
        return sideAEntryId;
    }

    public void setSideAEntryId(Long sideAEntryId) {
        this.sideAEntryId = sideAEntryId;
    }

    public Long getSideBEntryId() {
        return sideBEntryId;
    }

    public void setSideBEntryId(Long sideBEntryId) {
        this.sideBEntryId = sideBEntryId;
    }

    public String getSideASource() {
        return sideASource;
    }

    public void setSideASource(String sideASource) {
        this.sideASource = sideASource;
    }

    public String getSideBSource() {
        return sideBSource;
    }

    public void setSideBSource(String sideBSource) {
        this.sideBSource = sideBSource;
    }

    public Long getWinnerNextMatchId() {
        return winnerNextMatchId;
    }

    public void setWinnerNextMatchId(Long winnerNextMatchId) {
        this.winnerNextMatchId = winnerNextMatchId;
    }

    public String getWinnerNextSlot() {
        return winnerNextSlot;
    }

    public void setWinnerNextSlot(String winnerNextSlot) {
        this.winnerNextSlot = winnerNextSlot;
    }

    public Long getLoserNextMatchId() {
        return loserNextMatchId;
    }

    public void setLoserNextMatchId(Long loserNextMatchId) {
        this.loserNextMatchId = loserNextMatchId;
    }

    public String getLoserNextSlot() {
        return loserNextSlot;
    }

    public void setLoserNextSlot(String loserNextSlot) {
        this.loserNextSlot = loserNextSlot;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getWinnerEntryId() {
        return winnerEntryId;
    }

    public void setWinnerEntryId(Long winnerEntryId) {
        this.winnerEntryId = winnerEntryId;
    }

    public Long getLoserEntryId() {
        return loserEntryId;
    }

    public void setLoserEntryId(Long loserEntryId) {
        this.loserEntryId = loserEntryId;
    }

    public String getResultType() {
        return resultType;
    }

    public void setResultType(String resultType) {
        this.resultType = resultType;
    }

    public String getResultNote() {
        return resultNote;
    }

    public void setResultNote(String resultNote) {
        this.resultNote = resultNote;
    }

    public String getConfirmedBy() {
        return confirmedBy;
    }

    public void setConfirmedBy(String confirmedBy) {
        this.confirmedBy = confirmedBy;
    }

    public LocalDateTime getConfirmedAt() {
        return confirmedAt;
    }

    public void setConfirmedAt(LocalDateTime confirmedAt) {
        this.confirmedAt = confirmedAt;
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
