package com.billiard.club.dto.view;

/** 单次纠错中受影响的一个场次：变更类型 + 纠错前后 JSON 快照。 */
public record RevisionMatchView(
        Long matchId,
        String matchNo,
        String stage,
        String stageName,
        Integer roundNo,
        String changeType,
        String changeTypeName,
        String beforeJson,
        String afterJson) {
}
