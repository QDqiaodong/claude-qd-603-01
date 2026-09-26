package com.billiard.club.dto;

/** 结算请求体。payMode 必填：BALANCE / CASH / WECHAT / CARD。 */
public record SessionSettleRequest(
        String payMode) {
}
