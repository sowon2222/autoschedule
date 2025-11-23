package com.example.sbb.repository;

import com.example.sbb.domain.SlotLock;
import jakarta.persistence.LockModeType;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

// 슬롯 락 리포지토리 인터페이스
public interface SlotLockRepository extends JpaRepository<SlotLock, String> {

    // 슬롯 락 조회 (락 모드: 비관적 락)
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select sl from SlotLock sl where sl.slotKey = :slotKey")
    Optional<SlotLock> findBySlotKeyForUpdate(@Param("slotKey") String slotKey);

    // 슬롯 락 삭제 (사용자 ID 또는 사용자 ID가 없는 경우)
    @Modifying
    @Query("""
        delete from SlotLock sl
        where sl.slotKey = :slotKey
          and (
              (:userId is null and sl.user is null)
              or (:userId is not null and sl.user.id = :userId)
          )
        """)
    int deleteBySlotKeyAndUserId(@Param("slotKey") String slotKey, @Param("userId") Long userId);

    // 만료된 슬롯 락 삭제
    @Modifying
    @Query("delete from SlotLock sl where sl.expiresAt <= :now")
    int deleteExpired(@Param("now") OffsetDateTime now);
}

