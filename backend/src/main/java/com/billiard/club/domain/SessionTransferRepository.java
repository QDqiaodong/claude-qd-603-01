package com.billiard.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionTransferRepository extends JpaRepository<SessionTransfer, Long> {

    /** 一局的全部换台轨迹，按发生顺序升序 */
    List<SessionTransfer> findBySessionIdOrderByIdAsc(Long sessionId);

    /** 按幂等键查重：同一次换台请求重复提交时命中 */
    Optional<SessionTransfer> findByRequestId(String requestId);
}
