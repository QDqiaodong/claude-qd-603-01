package com.billiard.club.dto.view;

import java.time.LocalDateTime;

/**
 * 场次视图：括号里的一个方框。
 *
 * 来源/去向都已解析成可直接显示的中文与参赛者：
 *   slotA/slotB  —— 两个来源（种子 / 某场胜败者 / 轮空），resolvedEntrant 为当前占位人；
 *   nextWinText / nextLoseText —— 胜者/败者下一场去向（括号连线说明）；
 *   roundTitle   —— 当前所属轮次名；
 *   canConfirm   —— 双方到齐、未出结果、未完赛、非轮空 => 当前可确认赛果；
 *   canRollback  —— 已出结果且下游尚未长出任何结果时也允许普通修正？不：
 *                   只要长出下游结果就必须走管理员回退；这里统一只给管理员回退入口。
 */
public record MatchView(
        Long id,
        String matchCode,
        String bracketPart,
        String bracketPartName,
        Integer roundNo,
        String roundStage,
        String roundTitle,
        Integer seqInRound,
        SlotView slotA,
        SlotView slotB,
        String nextWinText,
        String nextLoseText,
        Boolean structuralBye,
        String status,
        String statusName,
        Long winnerEntrantId,
        Long loserEntrantId,
        String winKind,
        String winKindName,
        String confirmedBy,
        LocalDateTime confirmedAt,
        Boolean canConfirm,
        Boolean pendingCorrection,
        Boolean derivedFinished) {
}
