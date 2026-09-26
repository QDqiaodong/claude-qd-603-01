package com.billiard.club.dto.view;

import java.math.BigDecimal;

/** 器材视图。rentCount 来自实体的 @Formula 只读计算列。 */
public record GearView(
        Long id,
        String gearNo,
        String gearName,
        String gearType,
        String gearTypeName,
        BigDecimal price,
        String status,
        String statusName,
        Long rentCount,
        Long currentRentalId,
        String currentRentalNo,
        String currentHolderName) {
}
