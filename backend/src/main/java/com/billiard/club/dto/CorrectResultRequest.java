package com.billiard.club.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * 回退点的结果更正：在管理员回退之后，按更正后的胜方重新推进锚点，
 * 之后括号确定性重建（退赛弃权链也会按新结果重新物化）。
 */
public record CorrectResultRequest(
        @NotNull(message = "请选择更正后的胜方") Long winnerEntrantId,
        @NotBlank(message = "纠错必须填写原因") String reason,
        @NotBlank(message = "请填写管理员操作人") String operatorName) {
}
