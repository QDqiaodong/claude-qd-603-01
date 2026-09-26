package com.billiard.club.dto.view;

import java.math.BigDecimal;

/**
 * 球台视图。
 * openCount / totalMinutes 来自实体的 @Formula 只读计算列，直接透出去给平面图用。
 */
public record TableView(
        Long id,
        String tableNo,
        String areaName,
        String tableType,
        String tableTypeName,
        BigDecimal hourlyRate,
        String status,
        String statusName,
        String remark,
        Long openCount,
        Long totalMinutes,
        Long currentSessionId,
        String currentSessionNo,
        String currentMemberName) {
}
