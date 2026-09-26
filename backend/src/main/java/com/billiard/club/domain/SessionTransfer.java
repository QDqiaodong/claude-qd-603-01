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
 * 换台轨迹（只增不改）。
 *
 * 一次换台 = 关闭当前计费段 + 开启下一段 + 故障台转维护 + 目标台转使用中，
 * 全部落在同一个事务里。requestId 是幂等键：前端每次发起换台生成一次，
 * 连点 / 网络重试都带同一个，服务端凭它保证只产生一条轨迹、一个计费分段。
 */
@Entity
@Table(name = "session_transfer")
public class SessionTransfer {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** 必填外键：属于哪一局（换台不换单号） */
    @Column(name = "session_id", nullable = false)
    private Long sessionId;

    /** 必填外键：换出的故障台（换台成功后转维护中） */
    @Column(name = "from_table_id", nullable = false)
    private Long fromTableId;

    /** 必填外键：换入的目标台（换台成功后转使用中） */
    @Column(name = "to_table_id", nullable = false)
    private Long toTableId;

    /** 换台原因：台呢破损、灯光故障等 */
    @Column(name = "reason", nullable = false, length = 255)
    private String reason;

    /** 幂等键：同一次换台请求重复提交只生效一次 */
    @Column(name = "request_id", nullable = false, unique = true, length = 64)
    private String requestId;

    /** 换台时刻 = 原台结束时刻 = 目标台接续时刻 */
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

    public Long getSessionId() {
        return sessionId;
    }

    public void setSessionId(Long sessionId) {
        this.sessionId = sessionId;
    }

    public Long getFromTableId() {
        return fromTableId;
    }

    public void setFromTableId(Long fromTableId) {
        this.fromTableId = fromTableId;
    }

    public Long getToTableId() {
        return toTableId;
    }

    public void setToTableId(Long toTableId) {
        this.toTableId = toTableId;
    }

    public String getReason() {
        return reason;
    }

    public void setReason(String reason) {
        this.reason = reason;
    }

    public String getRequestId() {
        return requestId;
    }

    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
}
