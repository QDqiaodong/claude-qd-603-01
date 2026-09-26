package com.billiard.club.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface SessionSegmentRepository extends JpaRepository<SessionSegment, Long> {

    /** 一局的全部计费分段，按段序号升序（= 时间顺序） */
    List<SessionSegment> findBySessionIdOrderBySeqAsc(Long sessionId);

    /** 经过某张台的全部分段（含换台路过的），用于反查「该台的开局单」 */
    List<SessionSegment> findByTableId(Long tableId);
}
