package com.billiard.club.domain;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

import org.hibernate.annotations.Formula;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/**
 * 会员。
 *
 * 业务规则：卡号唯一 / 余额不足拦（开局与余额结算）/ 冻结卡禁开局、禁充值 /
 * 等级由累计充值只升不降。
 *
 * activeOrderCount 是 @Formula 只读计算列：该会员当前还有几局在进行中。
 */
@Entity
@Table(name = "club_member")
public class Member {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "card_no", nullable = false, unique = true, length = 32)
    private String cardNo;

    @Column(name = "name", nullable = false, length = 64)
    private String name;

    @Column(name = "phone", length = 32)
    private String phone;

    /** NORMAL 普通 / SILVER 银卡 / GOLD 金卡 / DIAMOND 钻石卡 */
    @Column(name = "level", nullable = false, length = 16)
    private String level;

    /** 卡内余额，只能被充值与余额结算变动 */
    @Column(name = "balance", nullable = false)
    private BigDecimal balance;

    /** 累计充值金额，用于只升不降地算等级 */
    @Column(name = "total_recharge", nullable = false)
    private BigDecimal totalRecharge;

    /** NORMAL 正常 / FROZEN 冻结 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "joined_at")
    private LocalDate joinedAt;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 只读计算列：该会员当前进行中的局数（status = 'OPEN'） */
    @Formula("(SELECT COUNT(*) FROM table_session s WHERE s.member_id = id AND s.status = 'OPEN')")
    private Long activeOrderCount;

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

    public String getCardNo() {
        return cardNo;
    }

    public void setCardNo(String cardNo) {
        this.cardNo = cardNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
    }

    public String getLevel() {
        return level;
    }

    public void setLevel(String level) {
        this.level = level;
    }

    public BigDecimal getBalance() {
        return balance;
    }

    public void setBalance(BigDecimal balance) {
        this.balance = balance;
    }

    public BigDecimal getTotalRecharge() {
        return totalRecharge;
    }

    public void setTotalRecharge(BigDecimal totalRecharge) {
        this.totalRecharge = totalRecharge;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDate getJoinedAt() {
        return joinedAt;
    }

    public void setJoinedAt(LocalDate joinedAt) {
        this.joinedAt = joinedAt;
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

    public Long getActiveOrderCount() {
        return activeOrderCount;
    }
}
