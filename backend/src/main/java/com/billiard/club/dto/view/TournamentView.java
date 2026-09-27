package com.billiard.club.dto.view;

import java.time.LocalDateTime;
import java.util.List;

/** 赛事详情视图：头信息 + 参赛者 + 完整括号（含来源/去向/当前轮次/待上场人员）+ 纠错流水。 */
public record TournamentView(
        Long id,
        String tournamentName,
        Integer entrantCount,
        Integer bracketSize,
        Integer byeCount,
        String status,
        String statusName,
        Long championId,
        EntrantView champion,
        Integer currentVersion,
        String createdBy,
        LocalDateTime createdAt,
        List<EntrantView> entrants,
        List<RoundView> rounds,
        List<MatchView> matches,
        List<RevisionView> revisions,
        List<MatchView> readyMatches,
        String resetCode) {
}
