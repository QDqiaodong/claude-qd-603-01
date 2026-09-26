package com.billiard.club.dto.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 充值流水视图：带充值前 / 充值后的余额与等级快照。 */
public record RechargeView(
        Long id,
        Long memberId,
        String cardNo,
        String memberName,
        BigDecimal amount,
        BigDecimal beforeBalance,
        BigDecimal afterBalance,
        String beforeLevel,
        String afterLevel,
        String payMode,
        String payModeName,
        LocalDateTime createdAt) {
}
