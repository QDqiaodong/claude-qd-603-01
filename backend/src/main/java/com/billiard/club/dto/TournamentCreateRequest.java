package com.billiard.club.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 创建赛事：名称 + 参赛者名单（数组顺序即种子顺序）。
 * 每项：memberId 非空 = 会员参赛者；guestName 非空 = 临时参赛者。
 */
public record TournamentCreateRequest(
        @NotBlank(message = "赛事名称不能为空")
        @Size(max = 128, message = "赛事名称最长 128 字")
        String name,

        @Size(min = 4, max = 64, message = "开赛人数需在 4 ~ 64 人之间")
        List<EntrySeed> entries) {

    public record EntrySeed(
            Long memberId,
            String guestName) {
    }
}
