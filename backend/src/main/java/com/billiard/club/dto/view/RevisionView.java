package com.billiard.club.dto.view;

import java.time.LocalDateTime;
import java.util.List;

/** 纠错流水视图：版本、操作人、原因、锚点、受影响场次都可追溯。 */
public record RevisionView(
        Long id,
        Integer versionNo,
        String changeType,
        String changeTypeName,
        String operatorName,
        String reason,
        Long anchorMatchId,
        String anchorMatchCode,
        List<Long> affectedMatchIds,
        List<String> affectedMatchCodes,
        LocalDateTime createdAt) {
}
