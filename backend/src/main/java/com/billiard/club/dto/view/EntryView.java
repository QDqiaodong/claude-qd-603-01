package com.billiard.club.dto.view;

/**
 * 参赛者视图。lossCount 与 status 组合把三种情况讲清楚：
 *   0 + ACTIVE 在胜者组 / 1 + ACTIVE 一败在败者组 / 2 + ELIMINATED 两败出局 /
 *   * + WITHDRAWN 中途退赛（未出场 lossCount=0，已一败 lossCount=1）。
 */
public record EntryView(
        Long id,
        Long memberId,
        String cardNo,
        String entryType,
        String entryTypeName,
        String displayName,
        Integer seedNo,
        Integer lossCount,
        String status,
        String statusName,
        String withdrawNote) {
}
