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
 * 球台。
 *
 * 业务规则：台号唯一 / 状态机（空闲 IDLE · 使用中 OCCUPIED · 维护中 MAINTENANCE）/ 维护中禁止开局。
 *
 * 注意：下面两个字段是 Hibernate 派生的【只读计算列】，不落库、不参与 insert/update，
 * 每次查询由 SQL 子查询现算，接口直接把它当普通字段返回。
 */
@Entity
@Table(name = "billiard_table")
public class BilliardTable {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "table_no", nullable = false, unique = true, length = 32)
    private String tableNo;

    @Column(name = "area_name", nullable = false, length = 64)
    private String areaName;

    /** CHINESE_EIGHT 中式黑八 / SNOOKER 斯诺克 / AMERICAN_NINE 美式九球 */
    @Column(name = "table_type", nullable = false, length = 16)
    private String tableType;

    @Column(name = "hourly_rate", nullable = false)
    private BigDecimal hourlyRate;

    /** IDLE 空闲 / OCCUPIED 使用中 / MAINTENANCE 维护中 */
    @Column(name = "status", nullable = false, length = 16)
    private String status;

    @Column(name = "remark", length = 255)
    private String remark;

    @Column(name = "created_at")
    private LocalDateTime createdAt;

    @Column(name = "updated_at")
    private LocalDateTime updatedAt;

    /** 只读计算列：经过该台的局数（含换台路过，按计费分段去重） */
    @Formula("(SELECT COUNT(DISTINCT g.session_id) FROM session_segment g WHERE g.table_id = id)")
    private Long openCount;

    /** 只读计算列：该台累计使用分钟数（按计费分段统计，未关闭的段不计入） */
    @Formula("(SELECT COALESCE(SUM(g.minutes), 0) FROM session_segment g WHERE g.table_id = id)")
    private Long totalMinutes;

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

    public String getTableNo() {
        return tableNo;
    }

    public void setTableNo(String tableNo) {
        this.tableNo = tableNo;
    }

    public String getAreaName() {
        return areaName;
    }

    public void setAreaName(String areaName) {
        this.areaName = areaName;
    }

    public String getTableType() {
        return tableType;
    }

    public void setTableType(String tableType) {
        this.tableType = tableType;
    }

    public BigDecimal getHourlyRate() {
        return hourlyRate;
    }

    public void setHourlyRate(BigDecimal hourlyRate) {
        this.hourlyRate = hourlyRate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public String getRemark() {
        return remark;
    }

    public void setRemark(String remark) {
        this.remark = remark;
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

    public Long getOpenCount() {
        return openCount;
    }

    public Long getTotalMinutes() {
        return totalMinutes;
    }
}
