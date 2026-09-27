package com.billiard.club.dto.view;

import java.time.LocalDateTime;

/** 参赛者视图：progress 为赛事模块内的状态（未出场 / 胜者组 / 败者组一败 / 已出局 / 已退赛）。 */
public record EntrantView(
        Long id,
        Long memberId,
        String displayName,
        String entrantKind,
        String entrantKindName,
        Integer seedNo,
        Boolean withdrawn,
        String withdrawReason,
        LocalDateTime withdrewAt,
        Integer losses,
        String progress,
        String progressName,
        Boolean played) {
}
