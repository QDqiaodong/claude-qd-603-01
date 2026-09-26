package com.billiard.club.dto.view;

/**
 * 换台结果。
 * duplicated = true 表示同一个 requestId 已经受理过（连点 / 网络重试），
 * 本次没有重复换台，返回的是当前最新状态。
 */
public record SessionTransferResultView(
        SessionView session,
        SessionTransferView transfer,
        Boolean duplicated) {
}
