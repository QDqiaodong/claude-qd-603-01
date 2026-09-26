package com.billiard.club.dto;

import java.math.BigDecimal;

/** 球台新增 / 局部修改请求体。字段为 null 表示「本次不改这一项」。 */
public record TableRequest(
        String tableNo,
        String areaName,
        String tableType,
        BigDecimal hourlyRate,
        String status,
        String remark) {
}
