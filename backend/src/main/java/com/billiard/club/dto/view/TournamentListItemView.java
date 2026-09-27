package com.billiard.club.dto.view;

import java.time.LocalDateTime;

/** 赛事列表项。 */
public record TournamentListItemView(
        Long id,
        String tournamentNo,
        String name,
        String status,
        String statusName,
        Integer playerCount,
        Integer bracketSize,
        Long championEntryId,
        String championName,
        LocalDateTime createdAt) {
}
