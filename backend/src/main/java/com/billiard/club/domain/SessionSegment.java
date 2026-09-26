package com.billiard.club.domain;

import java.math.BigDecimal;
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
 * 计费分段（一局至少一段）。
 *
 * 业务规则：开局即落地第一段；每次换台关闭当前段（记录结束时刻与实际分钟）并开启下一段
 * （接续时刻 = 上一段的结束时刻）。段的单价是「该段开始时」的台价快照，
 * 之后球台改价、后续再换台都不影响已关闭的历史段。
 * 结束计费时按各段各自的单价 × 实际分钟合并，再统一套会员折扣。
 */
@Entity
@Table(name = "session_segment")
public class SessionSegment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 必填外键：属于哪一局 */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 必填外键：本段用的是哪张台 */
    @Column(name = "table_id", nullable = false)
    private Long tableId;

    /** 段序号：从 1 开始，每次换台 +1（与 session_id 联合唯一） */
    @Column(name = "seq", nullable = false)
    private Integer seq;

    /** 本段开始时刻：第一段 = 开局时刻，后续段 = 换台接续时刻 */
    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /** 本段结束时刻：进行中的段为 null */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 本段实际使用分钟数：关闭时算出 */
    @Column(name = "minutes")
    private Integer minutes;

    /** 本段计费分钟数：未换过台的局不足一小时按一小时，换过台的局按实际分钟 */
    @Column(name = "billable_minutes")
    private Integer billableMinutes;

    /** 本段开始时的台价快照（历史段不随后续改价变化） */
    @Column(name = "hourly_rate", nullable = false)
    private BigDecimal hourlyRate;

    /** 本段小计 = 快照单价 × 计费分钟 / 60，关闭时算出 */
    @Column(name = "amount")
    private BigDecimal amount;

    /** TRANSFER 换台关闭 / CLOSE 结账关闭，未关闭为 null */
    @Column(name = "close_cause", length = 16)
    private String closeCause;

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

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public Integer getSeq() {
        return seq;
    }

    public void setSeq(Integer seq) {
        this.seq = seq;
    }

    public LocalDateTime getStartTime() {
        return startTime;
    }

    public void setStartTime(LocalDateTime startTime) {
        this.startTime = startTime;
    }

    public LocalDateTime getEndTime() {
        return endTime;
    }

    public void setEndTime(LocalDateTime endTime) {
        this.endTime = endTime;
    }

    public Integer getMinutes() {
        return minutes;
    }

    public void setMinutes(Integer minutes) {
        this.minutes = minutes;
    }

    public Integer getBillableMinutes() {
        return billableMinutes;
    }

    public void setBillableMinutes(Integer billableMinutes) {
        this.billableMinutes = billableMinutes;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public String getCloseCause() {
        return closeCause;
    }

    public void setCloseCause(String closeCause) {
        this.closeCause = closeCause;
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
