package com.billiard.club.dto.view;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 会员视图。activeOrderCount 来自实体的 @Formula 只读计算列。 */
public record MemberView(
        Long id,
        String cardNo,
        String name,
        String phone,
        String level,
        String levelName,
        BigDecimal discount,
        BigDecimal balance,
        BigDecimal totalRecharge,
        String status,
        String statusName,
        LocalDate joinedAt,
        Long activeOrderCount) {
}
