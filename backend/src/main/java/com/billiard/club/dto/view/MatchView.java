package com.billiard.club.dto.view;

import java.time.LocalDateTime;

/**
 * 场次视图：来源（sideXSource）、去向（winnerNextNo / loserNextNo）、
 * 当前状态、待上场人员（READY 时两侧即「待上场人员」）齐全。
 */
public record MatchView(
        Long id,
        String matchNo,
        String stage,
        String stageName,
        Integer roundNo,
        Integer seq,
        Long sideAEntryId,
        String sideAName,
        String sideASource,
        Long sideBEntryId,
        String sideBName,
        String sideBSource,
        Long winnerNextMatchId,
        String winnerNextNo,
        String winnerNextSlot,
        Long loserNextMatchId,
        String loserNextNo,
        String loserNextSlot,
        String status,
        String statusName,
        Long winnerEntryId,
        String winnerName,
        Long loserEntryId,
        String loserName,
        String resultType,
        String resultTypeName,
        boolean realMatch,
        String resultNote,
        String confirmedBy,
        LocalDateTime confirmedAt) {
}
