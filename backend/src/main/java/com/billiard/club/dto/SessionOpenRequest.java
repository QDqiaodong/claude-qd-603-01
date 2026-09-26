package com.billiard.club.dto;

/** 开局请求体。tableId 必填；memberId 为 null 表示散客开局。 */
public record SessionOpenRequest(
        Long tableId,
        Long memberId) {
}
