package com.billiard.club.dto.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 开局单视图：带上球台号、会员名与计费明细，便于直接展示结算金额。
 * segments 是按时间顺序的计费分段（每段各自单价快照），transfers 是换台轨迹。
 */
public record SessionView(
        Long id,
        String sessionNo,
        Long tableId,
        String tableNo,
        String areaName,
        Long memberId,
        String memberName,
        String memberLevel,
        String memberLevelName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer minutes,
        Integer billableMinutes,
        BigDecimal hourlyRate,
        BigDecimal amount,
        BigDecimal discount,
        BigDecimal payAmount,
        String status,
        String statusName,
        String payMode,
        String payModeName,
        List<SessionSegmentView> segments,
        List<SessionTransferView> transfers) {
}
