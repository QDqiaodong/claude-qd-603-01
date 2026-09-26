package com.billiard.club.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.billiard.club.common.ApiResponse;
import com.billiard.club.dto.view.RechargeView;
import com.billiard.club.service.MemberService;

/** 模块四附带的全局充值流水（只读） */
@RestController
@RequestMapping("/api/recharges")
public class RechargeController {

    private final MemberService memberService;

    public RechargeController(MemberService memberService) {
        this.memberService = memberService;
    }

    @GetMapping
    public ApiResponse<List<RechargeView>> list() {
        return ApiResponse.success(memberService.allRecharges());
    }
}
