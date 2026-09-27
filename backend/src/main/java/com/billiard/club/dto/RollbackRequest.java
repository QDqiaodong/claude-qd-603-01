package com.billiard.club.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 受控回退纠错：
 *   回退该场及其全部派生对阵（沿胜者/败者去向边），另一分支原样保留；
 *   correctedWinnerEntryId 非空时，回退后按更正后的胜者确定性重建后续括号；
 *   为空时只回退到待确认状态。
 */
public record RollbackRequest(
        @NotBlank(message = "纠错必须由管理员说明操作人")
        String operator,

        @NotBlank(message = "纠错必须填写原因")
        String reason,

        Long correctedWinnerEntryId) {
}
