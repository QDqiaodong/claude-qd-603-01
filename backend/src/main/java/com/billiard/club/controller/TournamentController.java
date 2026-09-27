package com.billiard.club.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.billiard.club.common.ApiResponse;
import com.billiard.club.dto.MatchResultRequest;
import com.billiard.club.dto.RollbackRequest;
import com.billiard.club.dto.TournamentCreateRequest;
import com.billiard.club.dto.WithdrawRequest;
import com.billiard.club.dto.view.RevisionView;
import com.billiard.club.dto.view.TournamentDetailView;
import com.billiard.club.dto.view.TournamentListItemView;
import com.billiard.club.service.TournamentService;

import jakarta.validation.Valid;

/**
 * 模块五（双败淘汰赛事）：
 *   创建赛事并生成完整括号 / 赛事详情（参赛者 + 对阵 + 冠军）/
 *   确认赛果 / 中途退赛 / 受控回退纠错 / 纠错版本审计。
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
    public ApiResponse<TournamentDetailView> detail(@PathVariable Long id) {
        return ApiResponse.success(tournamentService.detail(id));
    }

    @GetMapping("/{id}/revisions")
    public ApiResponse<List<RevisionView>> revisions(@PathVariable Long id) {
        return ApiResponse.success(tournamentService.revisions(id));
    }

    @PostMapping
    public ApiResponse<TournamentDetailView> create(@Valid @RequestBody TournamentCreateRequest request) {
        return ApiResponse.success("赛事已创建，完整双败括号已生成", tournamentService.create(request));
    }

    @PostMapping("/matches/{matchId}/result")
    public ApiResponse<TournamentDetailView> confirmResult(
            @PathVariable Long matchId,
            @RequestBody MatchResultRequest request) {
        return ApiResponse.success("赛果已确认，括号已按双败规则推进",
                tournamentService.confirmResult(matchId, request));
    }

    @PostMapping("/{tournamentId}/entries/{entryId}/withdraw")
    public ApiResponse<TournamentDetailView> withdraw(
            @PathVariable Long tournamentId,
            @PathVariable Long entryId,
            @Valid @RequestBody WithdrawRequest request) {
        return ApiResponse.success("退赛已登记，后续对阵已自动处理",
                tournamentService.withdraw(tournamentId, entryId, request));
    }

    @PostMapping("/matches/{matchId}/rollback")
    public ApiResponse<TournamentDetailView> rollback(
            @PathVariable Long matchId,
            @Valid @RequestBody RollbackRequest request) {
        return ApiResponse.success("已按原因回退并确定性重建后续括号",
                tournamentService.rollback(matchId, request));
    }
}
