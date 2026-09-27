package com.billiard.club.dto;

import jakarta.validation.constraints.NotNull;

/** 赛果确认：winnerEntrantId 为该场实际胜者，双方必须已到齐且尚未出结果。 */
public record MatchResultRequest(
        @NotNull(message = "请选择胜方") Long winnerEntrantId,
        String operatorName) {
}
