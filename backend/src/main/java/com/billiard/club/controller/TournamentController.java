package com.billiard.club.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.billiard.club.common.ApiResponse;
import com.billiard.club.dto.CorrectResultRequest;
import com.billiard.club.dto.EntrantWithdrawRequest;
import com.billiard.club.dto.MatchResultRequest;
import com.billiard.club.dto.RollbackRequest;
import com.billiard.club.dto.TournamentCreateRequest;
import com.billiard.club.dto.view.TournamentListItemView;
import com.billiard.club.dto.view.TournamentView;
import com.billiard.club.service.TournamentService;

import jakarta.validation.Valid;

/**
 * 模块五：双败淘汰赛事。
 *  - 建赛时一次生成胜者组/败者组/总决赛完整括号（非二的幂自动轮空）；
 *  - 赛果确认按双败规则推进，总决赛可生成重置赛；
 *  - 退赛区分尚未出场/一败/出局；已出结果只能管理员回退后纠错重建。
 */
@RestController
@RequestMapping("/api/tournaments")
public class TournamentController {

    private final TournamentService tournamentService;

    public TournamentController(TournamentService tournamentService) {
        this.tournamentService = tournamentService;
    }

    @GetMapping
    public ApiResponse<List<TournamentListItemView>> list() {
        return ApiResponse.success(tournamentService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<TournamentView> detail(@PathVariable Long id) {
        return ApiResponse.success(tournamentService.detail(id));
    }

    @PostMapping
    public ApiResponse<TournamentView> create(@Valid @RequestBody TournamentCreateRequest request) {
        return ApiResponse.success("赛事已创建，完整对阵已生成", tournamentService.create(request));
    }

    @PostMapping("/{id}/matches/{matchId}/result")
    public ApiResponse<TournamentView> confirmResult(@PathVariable Long id,
                                                      @PathVariable Long matchId,
                                                      @RequestBody MatchResultRequest request) {
        return ApiResponse.success("赛果已确认，括号已按双败规则推进",
                tournamentService.confirmResult(id, matchId, request));
    }

    @PostMapping("/{id}/entrants/{entrantId}/withdraw")
    public ApiResponse<TournamentView> withdraw(@PathVariable Long id,
                                                 @PathVariable Long entrantId,
                                                 @Valid @RequestBody EntrantWithdrawRequest request) {
        return ApiResponse.success("退赛已登记，后续弃权对阵已自动推进",
                tournamentService.withdraw(id, entrantId, request));
    }

    @PostMapping("/{id}/rollback")
    public ApiResponse<TournamentView> rollback(@PathVariable Long id,
                                                 @Valid @RequestBody RollbackRequest request) {
        return ApiResponse.success("已回退该场及其派生对阵，等待更正后的结果",
                tournamentService.rollback(id, request));
    }

    @PostMapping("/{id}/matches/{matchId}/correct")
    public ApiResponse<TournamentView> correct(@PathVariable Long id,
                                                @PathVariable Long matchId,
                                                @Valid @RequestBody CorrectResultRequest request) {
        return ApiResponse.success("结果已更正，后续括号按更正结果确定性重建",
                tournamentService.correctResult(id, matchId, request));
    }
}
