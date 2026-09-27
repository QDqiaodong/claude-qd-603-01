package com.billiard.club.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 受控回退：管理员说明原因后，从锚点场次回退该场及全部派生对阵。
 * 回退后括号停在锚点待确认状态；不受影响的另一分支原样保留。
 */
public record RollbackRequest(
        @NotBlank(message = "回退必须填写原因") String reason,
        @NotBlank(message = "请填写管理员操作人") String operatorName,
        @NotNull(message = "缺少回退锚点场次") Long anchorMatchId) {
}
