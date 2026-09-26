package com.billiard.club.dto;

import java.math.BigDecimal;

/** 会员充值请求体。amount 必填，单次区间见 BilliardDict。 */
public record RechargeRequest(
        BigDecimal amount,
        String payMode) {
}
