package com.billiard.club.dto;

import java.math.BigDecimal;

/** 器材借出请求体。gearId 必填；memberId 为 null 表示散客借杆。 */
public record RentRequest(
        Long gearId,
        Long memberId,
        BigDecimal deposit) {
}
