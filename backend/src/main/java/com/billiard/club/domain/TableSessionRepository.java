package com.billiard.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface TableSessionRepository extends JpaRepository<TableSession, Long> {

    List<TableSession> findAllByOrderByIdDesc();

    /** 某张台上正在进行的局（同一球台同一时间只能有一局） */
    Optional<TableSession> findFirstByTableIdAndStatus(Long tableId, String status);

    /** 某会员正在进行的局数（冻结会员前要先确认没有未收尾的局） */
    long countByMemberIdAndStatus(Long memberId, String status);

    boolean existsBySessionNo(String sessionNo);

    /**
     * 行级写锁（SELECT ... FOR UPDATE）：换台 / 结束 / 结算先抢这把锁，
     * 保证同一开局单上只有一个状态转换能生效。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from TableSession s where s.id = :id")
    Optional<TableSession> findByIdForUpdate(@Param("id") Long id);
}
