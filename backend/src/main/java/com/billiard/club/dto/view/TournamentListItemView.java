package com.billiard.club.dto.view;

/** 赛事列表项（不含完整括号，供赛事选择列表使用）。 */
public record TournamentListItemView(
        Long id,
        String tournamentName,
        Integer entrantCount,
        Integer bracketSize,
        Integer byeCount,
        String status,
        String statusName,
        Integer currentVersion,
        String championName,
        String createdBy) {
}
