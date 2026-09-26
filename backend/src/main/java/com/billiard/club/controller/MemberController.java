package com.billiard.club.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.billiard.club.common.ApiResponse;
import com.billiard.club.dto.MemberRequest;
import com.billiard.club.dto.RechargeRequest;
import com.billiard.club.dto.view.MemberView;
import com.billiard.club.dto.view.RechargeView;
import com.billiard.club.service.MemberService;

/** 模块四（会员与充值）：列表 / 新增 / 局部修改 / 充值 / 冻结 / 解冻 / 充值流水 */
@RestController
@RequestMapping("/api/members")
public class MemberController {

    private final MemberService memberService;

    public MemberController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<List<MemberView>> list() {
        return ApiResponse.success(memberService.list());
    }

    @GetMapping("/{id}")
    public ApiResponse<MemberView> detail(@PathVariable Long id) {
        return ApiResponse.success(memberService.detail(id));
    }

    @GetMapping("/{id}/recharges")
    public ApiResponse<List<RechargeView>> recharges(@PathVariable Long id) {
        return ApiResponse.success(memberService.recharges(id));
    }

    @PostMapping
    public ApiResponse<MemberView> create(@RequestBody MemberRequest request) {
        return ApiResponse.success("会员已建档", memberService.create(request));
    }

    @PutMapping("/{id}")
    public ApiResponse<MemberView> update(@PathVariable Long id, @RequestBody MemberRequest request) {
        return ApiResponse.success("会员已更新", memberService.update(id, request));
    }

    @PostMapping("/{id}/recharge")
    public ApiResponse<RechargeView> recharge(@PathVariable Long id, @RequestBody RechargeRequest request) {
        return ApiResponse.success("充值成功", memberService.recharge(id, request));
    }

    @PostMapping("/{id}/freeze")
    public ApiResponse<MemberView> freeze(@PathVariable Long id) {
        return ApiResponse.success("会员卡已冻结", memberService.freeze(id));
    }

    @PostMapping("/{id}/unfreeze")
    public ApiResponse<MemberView> unfreeze(@PathVariable Long id) {
        return ApiResponse.success("会员卡已解冻", memberService.unfreeze(id));
    }
}
