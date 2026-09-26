package com.billiard.club.dto.view;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** 器材租借单视图。 */
public record RentalView(
        Long id,
        String rentalNo,
        Long gearId,
        String gearNo,
        String gearName,
        BigDecimal gearPrice,
        Long memberId,
        String memberName,
        LocalDateTime rentTime,
        LocalDateTime returnTime,
        String status,
        String statusName,
        BigDecimal deposit,
        BigDecimal damageFee,
        String damageNote) {
}
