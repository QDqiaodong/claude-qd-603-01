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
 * 器材租借单。
 *
 * 业务规则：借出未还禁止再借（同一器材同时只能有一条租借中的单）/
 * 归还时登记损坏并按 damage_fee 赔偿 / 归还与损坏登记都是单向动作，不能重复。
 */
@Entity
@Table(name = "gear_rental")
public class GearRental {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "rental_no", nullable = false, unique = true, length = 32)
    private String rentalNo;

    /** 必填外键：借出的器材 */
    @Column(name = "gear_id", nullable = false)
    private Long gearId;

    /** 可空外键：散客借杆时为 null */
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "rent_time", nullable = false)
    private LocalDateTime rentTime;

    @Column(name = "return_time")
    private LocalDateTime returnTime;

    /** RENTED 租借中 / RETURNED 已归还 / DAMAGED 损坏待赔 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "deposit")
    private BigDecimal deposit;

    /** 损坏赔偿金额，未损坏时为 null */
    @Column(name = "damage_fee")
    private BigDecimal damageFee;

    @Column(name = "damage_note", length = 255)
    private String damageNote;

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

    public String getRentalNo() {
        return rentalNo;
    }

    public void setRentalNo(String rentalNo) {
        this.rentalNo = rentalNo;
    }

    public Long getGearId() {
        return gearId;
    }

    public void setGearId(Long gearId) {
        this.gearId = gearId;
    }

    public Long getMemberId() {
        return memberId;
    }

    public void setMemberId(Long memberId) {
        this.memberId = memberId;
    }

    public LocalDateTime getRentTime() {
        return rentTime;
    }

    public void setRentTime(LocalDateTime rentTime) {
        this.rentTime = rentTime;
    }

    public LocalDateTime getReturnTime() {
        return returnTime;
    }

    public void setReturnTime(LocalDateTime returnTime) {
        this.returnTime = returnTime;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public BigDecimal getDeposit() {
        return deposit;
    }

    public void setDeposit(BigDecimal deposit) {
        this.deposit = deposit;
    }

    public BigDecimal getDamageFee() {
        return damageFee;
    }

    public void setDamageFee(BigDecimal damageFee) {
        this.damageFee = damageFee;
    }

    public String getDamageNote() {
        return damageNote;
    }

    public void setDamageNote(String damageNote) {
        this.damageNote = damageNote;
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
