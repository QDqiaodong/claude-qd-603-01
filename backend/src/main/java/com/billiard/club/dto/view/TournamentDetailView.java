package com.billiard.club.dto.view;

import java.time.LocalDateTime;
import java.util.List;

/** 赛事详情：基本信息 + 参赛者 + 场次 + 冠军。 */
public record TournamentDetailView(
        Long id,
        String tournamentNo,
        String name,
        String status,
        String statusName,
        Integer playerCount,
        Integer bracketSize,
        Integer byeCount,
        Long championEntryId,
        String championName,
        LocalDateTime createdAt,
        List<EntryView> entries,
        List<MatchView> matches) {
}
