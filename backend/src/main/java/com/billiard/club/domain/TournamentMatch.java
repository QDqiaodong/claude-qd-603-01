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
 * 赛事对阵场次。
 *
 * 结构（来源槽位 / 去向 / 轮空标记）在建赛时一次写死，之后永不改变；
 * 只有 status / winner / loser / winKind / 确认人/时间 会随赛果流转，
 * 且已确认的结果只能经管理员回退修改，普通操作只能往未出结果的场次推进。
 *
 * structuralBye 的场次是单方轮空占位：它不算比赛、没有结果、不占场数，
 * 读括号时把在场一方解析为占位胜者，败者槽为空（不向败者组落人）。
 */
@Entity
@Table(name = "tournament_match")
public class TournamentMatch {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "tournament_id", nullable = false)
    private Long tournamentId;

    @Column(name = "match_code", nullable = false, length = 32)
    private String matchCode;

    /** WB 胜者组 / LB 败者组 / GF 总决赛 */
    @Column(name = "bracket_part", nullable = false, length = 16)
    private String bracketPart;

    @Column(name = "round_no", nullable = false)
    private Integer roundNo;

    /** MINOR 败者组小轮 / MAJOR 败者组大轮 / FINAL 决赛 */
    @Column(name = "round_stage", nullable = false, length = 16)
    private String roundStage;

    @Column(name = "seq_in_round", nullable = false)
    private Integer seqInRound;

    @Column(name = "slot_a_source", nullable = false, length = 64)
    private String slotASource;

    @Column(name = "slot_b_source", nullable = false, length = 64)
    private String slotBSource;

    @Column(name = "slot_a_seed_no")
    private Integer slotASeedNo;

    @Column(name = "slot_b_seed_no")
    private Integer slotBSeedNo;

    @Column(name = "next_win", length = 64)
    private String nextWin;

    @Column(name = "next_lose", length = 64)
    private String nextLose;

    @Column(name = "structural_bye", nullable = false)
    private Boolean structuralBye = false;

    /** WAITING 待上场 / READY 可开打 / FINISHED 已出结果 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "winner_entrant_id")
    private Long winnerEntrantId;

    @Column(name = "loser_entrant_id")
    private Long loserEntrantId;

    /** NORMAL 正常完赛 / WALKOVER 对手退赛不战而胜 */
    @Column(name = "win_kind", length = 16)
    private String winKind;

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

    public String getMatchCode() {
        return matchCode;
    }

    public void setMatchCode(String matchCode) {
        this.matchCode = matchCode;
    }

    public String getBracketPart() {
        return bracketPart;
    }

    public void setBracketPart(String bracketPart) {
        this.bracketPart = bracketPart;
    }

    public Integer getRoundNo() {
        return roundNo;
    }

    public void setRoundNo(Integer roundNo) {
        this.roundNo = roundNo;
    }

    public String getRoundStage() {
        return roundStage;
    }

    public void setRoundStage(String roundStage) {
        this.roundStage = roundStage;
    }

    public Integer getSeqInRound() {
        return seqInRound;
    }

    public void setSeqInRound(Integer seqInRound) {
        this.seqInRound = seqInRound;
    }

    public String getSlotASource() {
        return slotASource;
    }

    public void setSlotASource(String slotASource) {
        this.slotASource = slotASource;
    }

    public String getSlotBSource() {
        return slotBSource;
    }

    public void setSlotBSource(String slotBSource) {
        this.slotBSource = slotBSource;
    }

    public Integer getSlotASeedNo() {
        return slotASeedNo;
    }

    public void setSlotASeedNo(Integer slotASeedNo) {
        this.slotASeedNo = slotASeedNo;
    }

    public Integer getSlotBSeedNo() {
        return slotBSeedNo;
    }

    public void setSlotBSeedNo(Integer slotBSeedNo) {
        this.slotBSeedNo = slotBSeedNo;
    }

    public String getNextWin() {
        return nextWin;
    }

    public void setNextWin(String nextWin) {
        this.nextWin = nextWin;
    }

    public String getNextLose() {
        return nextLose;
    }

    public void setNextLose(String nextLose) {
        this.nextLose = nextLose;
    }

    public Boolean getStructuralBye() {
        return structuralBye;
    }

    public void setStructuralBye(Boolean structuralBye) {
        this.structuralBye = structuralBye;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public Long getWinnerEntrantId() {
        return winnerEntrantId;
    }

    public void setWinnerEntrantId(Long winnerEntrantId) {
        this.winnerEntrantId = winnerEntrantId;
    }

    public Long getLoserEntrantId() {
        return loserEntrantId;
    }

    public void setLoserEntrantId(Long loserEntrantId) {
        this.loserEntrantId = loserEntrantId;
    }

    public String getWinKind() {
        return winKind;
    }

    public void setWinKind(String winKind) {
        this.winKind = winKind;
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
