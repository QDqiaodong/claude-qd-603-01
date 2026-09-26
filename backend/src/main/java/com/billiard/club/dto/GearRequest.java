package com.billiard.club.dto;

import java.math.BigDecimal;

/** 器材新增 / 局部修改请求体。 */
public record GearRequest(
        String gearNo,
        String gearName,
        String gearType,
        BigDecimal price,
        String status) {
}
