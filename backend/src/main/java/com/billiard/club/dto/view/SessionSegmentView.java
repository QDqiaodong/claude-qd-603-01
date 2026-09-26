package com.billiard.club.dto.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * 计费分段视图：一段一台，带当时单价快照与关闭原因。
 * current = true 表示这是正在进行的段（当前所在球台）。
 */
public record SessionSegmentView(
        Long id,
        Integer seq,
        Long tableId,
        String tableNo,
        String areaName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        Integer minutes,
        Integer billableMinutes,
        BigDecimal hourlyRate,
        BigDecimal amount,
        String closeCause,
        String closeCauseName,
        Boolean current) {
}
