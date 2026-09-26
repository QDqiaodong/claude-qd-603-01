package com.billiard.club.dto;

/** 会员新增 / 局部修改请求体。余额不在这里改（只能走充值或余额结算）。 */
public record MemberRequest(
        String cardNo,
        String name,
        String phone,
        String level,
        String status) {
}
