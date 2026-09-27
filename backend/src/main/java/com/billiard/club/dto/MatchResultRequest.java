package com.billiard.club.dto;

import jakarta.validation.constraints.NotNull;

/** 确认赛果：winnerEntryId 必须是本场已到齐的两名选手之一。 */
public record MatchResultRequest(
        @NotNull(message = "必须指定胜者")
        Long winnerEntryId,

        String operator,

        String note) {
}
