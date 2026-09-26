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
 * 开局单（一局球）。
 *
 * 业务规则：开局 -> 结束 -> 结算单向流程 / 同一球台同一时间只能有一局 /
 * 时长按分钟计价、不足一小时按一小时 / 会员折扣。
 *
 * 换台：进行中的局可换到同类型空闲台，单号与会员归属不变，只改当前所在台（table_id）；
 * 每段的开始 / 结束时刻、分钟数与当时单价落在 session_segment，换台过程落在 session_transfer。
 */
@Entity
@Table(name = "table_session")
public class TableSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "session_no", nullable = false, unique = true, length = 32)
    private String sessionNo;

    /** 必填外键：开哪张台 */
    @Column(name = "table_id", nullable = false)
    private Long tableId;

    /** 可空外键：散客开局时为 null，会员开局时挂会员卡 */
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "start_time", nullable = false)
    private LocalDateTime startTime;

    /** 结束时间：只有状态走到 CLOSED / SETTLED 才有值 */
    @Column(name = "end_time")
    private LocalDateTime endTime;

    /** 实际时长（分钟）：结束时算出来 */
    @Column(name = "minutes")
    private Integer minutes;

    /** 计费分钟数：不足一小时按一小时 */
    @Column(name = "billable_minutes")
    private Integer billableMinutes;

    /** 应收金额（未打折） */
    @Column(name = "amount")
    private BigDecimal amount;

    /** 实收金额（按会员等级折扣折算） */
    @Column(name = "pay_amount")
    private BigDecimal payAmount;

    /** 结算时套用的会员折扣率 */
    @Column(name = "discount")
    private BigDecimal discount;

    /** OPEN 进行中 / CLOSED 已结束待结算 / SETTLED 已结算 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    /** 结算支付方式，未结算时为 null */
    @Column(name = "pay_mode", length = 16)
    private String payMode;

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

    public String getSessionNo() {
        return sessionNo;
    }

    public void setSessionNo(String sessionNo) {
        this.sessionNo = sessionNo;
    }

    public Long getTableId() {
        return tableId;
    }

    public void setTableId(Long tableId) {
        this.tableId = tableId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
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

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public BigDecimal getPayAmount() {
        return payAmount;
    }

    public void setPayAmount(BigDecimal payAmount) {
        this.payAmount = payAmount;
    }

    public BigDecimal getDiscount() {
        return discount;
    }

    public void setDiscount(BigDecimal discount) {
        this.discount = discount;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getPayMode() {
        return payMode;
    }

    public void setPayMode(String payMode) {
        this.payMode = payMode;
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
