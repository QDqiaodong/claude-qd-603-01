package com.billiard.club.dto;

import jakarta.validation.constraints.NotBlank;

/** 中途退赛：区分未出场 / 已一败，仅记录退赛状态，不折算失利次数。 */
public record WithdrawRequest(
        @NotBlank(message = "必须填写操作人")
        String operator,

        String note) {
}
