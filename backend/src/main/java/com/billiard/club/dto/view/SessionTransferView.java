package com.billiard.club.dto.view;

import java.time.LocalDateTime;

/** 换台轨迹视图：从哪张台换到哪张台、为什么、什么时候。 */
public record SessionTransferView(
        Long id,
        Long fromTableId,
        String fromTableNo,
        Long toTableId,
        String toTableNo,
        String reason,
        LocalDateTime createdAt) {
}
