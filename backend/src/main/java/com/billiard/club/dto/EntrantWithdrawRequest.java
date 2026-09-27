package com.billiard.club.dto;

import jakarta.validation.constraints.NotBlank;

/** 中途退赛登记。reason 必填，便于后续晋级关系保持可解释。 */
public record EntrantWithdrawRequest(
        @NotBlank(message = "请填写退赛原因") String reason,
        String operatorName) {
}
