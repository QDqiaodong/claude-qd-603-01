package com.billiard.club.dto;

/**
 * 换台请求体。
 * targetTableId 必填：要换入的同类型空闲台；reason 必填：换台原因；
 * requestId 必填：幂等键，前端每次发起换台生成一次，连点 / 网络重试都带同一个。
 */
public record SessionTransferRequest(
        Long targetTableId,
        String reason,
        String requestId) {
}
