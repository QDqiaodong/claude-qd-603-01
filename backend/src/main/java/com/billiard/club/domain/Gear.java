package com.billiard.club.domain;

import java.math.BigDecimal;
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
 * 器材（球杆 / 手套 / 架杆 / 球组）。
 *
 * 业务规则：器材编号唯一 / 已借出未还禁止再借 / 损坏的器材维修完成前禁止借出。
 * rentCount 也是 @Formula 只读计算列，由 gear_rental 的计数子查询现算。
 */
@Entity
@Table(name = "gear")
public class Gear {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "gear_no", nullable = false, unique = true, length = 32)
    private String gearNo;

    @Column(name = "gear_name", nullable = false, length = 64)
    private String gearName;

    /** CUE 球杆 / GLOVE 手套 / BRIDGE 架杆 / BALL_SET 球组 */
    @Column(name = "gear_type", nullable = false, length = 16)
    private String gearType;

    /** 器材原价，损坏赔偿以此为上限 */
    @Column(name = "price", nullable = false)
    private BigDecimal price;

    /** AVAILABLE 在库 / RENTED 已借出 / DAMAGED 已损坏 / SCRAPPED 已报废 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 只读计算列：该器材累计被租借次数（含租借中与已归还） */
    @Formula("(SELECT COUNT(*) FROM gear_rental r WHERE r.gear_id = id)")
    private Long rentCount;

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

    public String getGearNo() {
        return gearNo;
    }

    public void setGearNo(String gearNo) {
        this.gearNo = gearNo;
    }

    public String getGearName() {
        return gearName;
    }

    public void setGearName(String gearName) {
        this.gearName = gearName;
    }

    public String getGearType() {
        return gearType;
    }

    public void setGearType(String gearType) {
        this.gearType = gearType;
    }

    public BigDecimal getPrice() {
        return price;
    }

    public void setPrice(BigDecimal price) {
        this.price = price;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
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

    public Long getRentCount() {
        return rentCount;
    }
}
