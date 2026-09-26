package com.billiard.club.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.billiard.club.common.ApiResponse;
import com.billiard.club.dto.SessionOpenRequest;
import com.billiard.club.dto.SessionSettleRequest;
import com.billiard.club.dto.SessionTransferRequest;
import com.billiard.club.dto.view.SessionTransferResultView;
import com.billiard.club.dto.view.SessionView;
import com.billiard.club.service.SessionService;

/** 模块二（开局与计费）：列表 / 进行中 / 开局 / 结束 / 结算 / 换台 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

    private final SessionService sessionService;

    public SessionController(SessionService sessionService) {
        this.sessionService = sessionService;
    }

    @GetMapping
    public ApiResponse<List<SessionView>> list() {
        return ApiResponse.success(sessionService.list());
    }

    @GetMapping("/open")
    public ApiResponse<List<SessionView>> openList() {
        return ApiResponse.success(sessionService.openList());
    }

    @GetMapping("/{id}")
    public ApiResponse<SessionView> detail(@PathVariable Long id) {
        return ApiResponse.success(sessionService.detail(id));
    }

    @GetMapping("/by-table/{tableId}")
    public ApiResponse<List<SessionView>> byTable(@PathVariable Long tableId) {
        return ApiResponse.success(sessionService.byTable(tableId));
    }

    @PostMapping
    public ApiResponse<SessionView> open(@RequestBody SessionOpenRequest request) {
        return ApiResponse.success("开局成功", sessionService.open(request));
    }

    @PostMapping("/{id}/close")
    public ApiResponse<SessionView> close(@PathVariable Long id) {
        return ApiResponse.success("已结束计费", sessionService.close(id));
    }

    @PostMapping("/{id}/settle")
    public ApiResponse<SessionView> settle(@PathVariable Long id, @RequestBody SessionSettleRequest request) {
        return ApiResponse.success("结算完成", sessionService.settle(id, request));
    }

    @PostMapping("/{id}/transfer")
    public ApiResponse<SessionTransferResultView> transfer(@PathVariable Long id,
                                                           @RequestBody SessionTransferRequest request) {
        SessionTransferResultView result = sessionService.transfer(id, request);
        if (Boolean.TRUE.equals(result.duplicated())) {
            return ApiResponse.success("该换台请求已受理过，本次为重复提交，未重复换台", result);
        }
        return ApiResponse.success("换台成功，原球台已转入维护", result);
    }
}
