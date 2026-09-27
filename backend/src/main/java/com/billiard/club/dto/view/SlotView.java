package com.billiard.club.dto.view;

/**
 * 场次的一个来源槽位。
 *
 * sourceKind：SEED 种子 / BYE 轮空 / WIN 某场胜者 / LOSE 某场败者；
 * sourceMatchCode/sourceSide：上游场次编号与取自胜/败；
 * resolvedEntrantId/Name：当前真正占位的参赛者（未到齐为 null）；
 * resolvedState：PRESENT 已到场 / WAITING 等待上游 / BYE 轮空 / WITHDRAWN 已退赛。
 */
public record SlotView(
        String sourceKind,
        String sourceText,
        String sourceMatchCode,
        String sourceSide,
        Integer seedNo,
        Long resolvedEntrantId,
        String resolvedEntrantName,
        String resolvedState,
        String resolvedStateName) {
}
