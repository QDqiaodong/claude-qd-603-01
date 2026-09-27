package com.billiard.club.dto.view;

import java.time.LocalDateTime;
import java.util.List;

/** 纠错版本视图：操作人、原因、更正胜者、受影响场次快照列表。 */
public record RevisionView(
        Long id,
        Long rootMatchId,
        String rootMatchNo,
        String operator,
        String reason,
        Long correctedWinnerEntryId,
        String correctedWinnerName,
        LocalDateTime createdAt,
        List<RevisionMatchView> matches) {
}
