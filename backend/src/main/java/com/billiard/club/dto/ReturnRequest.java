package com.billiard.club.dto;

import java.math.BigDecimal;

/**
 * 器材归还请求体。
 * damaged 为 true 时必须给出 damageFee（损坏赔偿金额），否则按正常归还处理。
 */
public record ReturnRequest(
        Boolean damaged,
        BigDecimal damageFee,
        String damageNote) {
}
