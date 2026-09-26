package com.billiard.club.domain;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

public interface BilliardTableRepository extends JpaRepository<BilliardTable, Long> {

    List<BilliardTable> findAllByOrderByAreaNameAscTableNoAsc();

    List<BilliardTable> findAllByOrderByIdAsc();

    Optional<BilliardTable> findByTableNo(String tableNo);

    boolean existsByTableNo(String tableNo);

    /**
     * 行级写锁（SELECT ... FOR UPDATE）：开局 / 换台先抢球台锁，
     * 两个前台同时抢同一张空闲台时只有一笔能成功。
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select t from BilliardTable t where t.id = :id")
    Optional<BilliardTable> findByIdForUpdate(@Param("id") Long id);
}
