package com.billiard.club.dto;

import java.util.List;

import jakarta.validation.constraints.NotBlank;

/**
 * 创建赛事请求。
 *
 * entrants 按顺序给出种子（第 1 个 = 1 号种子，自动占最优轮空位）；
 * 每项要么带 memberId（会员），要么带 walkInName（临时散客）。
 * operatorName 为当前店员，所有后续确认/回退操作都要带操作人。
 */
public record TournamentCreateRequest(
        String tournamentName,
        List<EntrantInput> entrants,
        @NotBlank(message = "操作人不能为空") String operatorName) {

    public record EntrantInput(
            Long memberId,
            String walkInName) {
    }
}
